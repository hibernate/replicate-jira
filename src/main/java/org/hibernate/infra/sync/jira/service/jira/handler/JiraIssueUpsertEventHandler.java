package org.hibernate.infra.sync.jira.service.jira.handler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hibernate.infra.sync.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraRemoteLink;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraSimpleObject;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraTextContent;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraTransition;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraUser;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

public class JiraIssueUpsertEventHandler extends JiraEventHandler {

	public JiraIssueUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id) {
		super( reportingConfig, context, id );
	}

	@Override
	protected void doRun() {
		JiraIssue sourceIssue = null;
		try {
			sourceIssue = context.sourceJiraClient().getIssue( objectId );
		}
		catch (JiraRestException e) {
			failureCollector.critical( "Source issue %d was not found through the REST API".formatted( objectId ), e );
			// no point in continuing anything
			return;
		}

		String destinationKey = toDestinationKey( sourceIssue.key );
		// We don't really need one, but doing so means that we will create the placeholder for it
		// if the issue wasn't already present in the destination Jira instance
		JiraIssue destinationIssue = getDestinationIssue( destinationKey );

		try {
			context.destinationJiraClient().update( destinationKey, issueToCreate( sourceIssue ) );
			// remote "aka web" links cannot be added in the same request and are also not returned as part of the issue API.
			// We "upsert" the remote link pointing to the "original/source" issue that triggered the sync with an additional call:
			context.destinationJiraClient().upsertRemoteLink( destinationKey, remoteSelfLink( sourceIssue ) );
			// issue status can be updated only through transition:
			prepareTransition( sourceIssue )
					.ifPresent( jiraTransition -> context.destinationJiraClient().transition( destinationKey, jiraTransition ) );
		}
		catch (JiraRestException e) {
			failureCollector.critical( "Unable to update destination issue %s: %s".formatted( destinationKey, e.getMessage() ), e );
		}
	}

	private JiraRemoteLink remoteSelfLink(JiraIssue sourceIssue) {
		URI jiraLink = createJiraIssueUri( sourceIssue );

		JiraRemoteLink link = new JiraRemoteLink();
		//  >> Setting this field enables the remote issue link details to be updated or deleted using remote system
		//  >>  and item details as the record identifier, rather than using the record's Jira ID.
		//
		//  Hence, we set this global id as a link to the issue, this way it should be unique enough and easy to create:
		link.globalId = jiraLink.toString();
		link.relationship = "Upstream issue";
		link.object.title = sourceIssue.key;
		link.object.url = jiraLink;
		link.object.summary = "Link to an upstream JIRA issue, from which this one was cloned from.";

		return link;
	}

	private JiraIssue issueToCreate(JiraIssue sourceIssue) {
		JiraIssue destinationIssue = new JiraIssue();
		destinationIssue.fields = new JiraFields();

		destinationIssue.fields.summary = sourceIssue.fields.summary;
		destinationIssue.fields.description = sourceIssue.fields.description;
		destinationIssue.fields.description = prepareDescriptionQuote( sourceIssue ) + sourceIssue.fields.description;

		destinationIssue.fields.labels = sourceIssue.fields.labels;
		// let's also add fix versions to the labels
		if ( sourceIssue.fields.fixVersions != null ) {
			if ( destinationIssue.fields.labels == null ) {
				destinationIssue.fields.labels = List.of();
			}
			destinationIssue.fields.labels = new ArrayList<>( destinationIssue.fields.labels );
			for ( JiraSimpleObject fixVersion : sourceIssue.fields.fixVersions ) {
				destinationIssue.fields.labels.add( "Fix version: %s".formatted( fixVersion.name ) );
			}
		}

		// if we can map the priority - great we'll do that, if no: we'll keep it blank and let Jira use its default instead:
		destinationIssue.fields.priority = priority( sourceIssue.fields.priority.id )
				.map( JiraSimpleObject::new ).orElse( null );

		destinationIssue.fields.project.id = context.project().projectId();

		destinationIssue.fields.issuetype = issueType( sourceIssue.fields.issuetype.id )
				.map( JiraSimpleObject::new ).orElse( null );

		// now let's handle the users. we will consider only a mapped subset of users and for other's the defaults will be used.
		// also the description is going to include a section mentioning who created and who the issue is assigned to...
		if ( context.projectGroup().canSetReporter() ) {
			destinationIssue.fields.reporter = user( sourceIssue.fields.reporter )
					.map( JiraUser::new ).orElse( null );
		}

		destinationIssue.fields.assignee = user( sourceIssue.fields.assignee )
				.map( JiraUser::new ).orElse( null );

		return destinationIssue;
	}

	private Optional<JiraTransition> prepareTransition(JiraIssue sourceIssue) {
		return statusToTransition( sourceIssue.fields.status.id )
				.map( tr -> new JiraTransition( tr, "Upstream issue status updated to: " + sourceIssue.fields.status.name ) );
	}

	private String prepareDescriptionQuote(JiraIssue issue) {
		URI issueUri = createJiraIssueUri( issue );
		URI reporterUri = createJiraUserUri( issue.self, issue.fields.reporter );
		URI assigneeUri = createJiraUserUri( issue.self, issue.fields.assignee );
		return """
				{quote}This issue is created as a copy of [%s|%s].
				
				Assigned to: %s.
				
				Reported by: [user %s|%s].{quote}
				
				
				""".formatted( issue.key, issueUri,
				assigneeUri == null ? " Unassigned" : "[user %s|%s]".formatted( JiraTextContent.userIdPart( issue.fields.assignee ), assigneeUri ),
				JiraTextContent.userIdPart( issue.fields.reporter ), reporterUri
		);
	}

}
