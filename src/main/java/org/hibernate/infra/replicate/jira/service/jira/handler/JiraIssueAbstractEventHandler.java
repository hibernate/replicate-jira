package org.hibernate.infra.replicate.jira.service.jira.handler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssueLink;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraRemoteLink;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraSimpleObject;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraTransition;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraUser;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

abstract class JiraIssueAbstractEventHandler extends JiraEventHandler {

	public JiraIssueAbstractEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id) {
		super(reportingConfig, context, id);
	}

	protected void applyTransition(JiraIssue sourceIssue, String destinationKey) {
		prepareTransition(sourceIssue).ifPresent(
				jiraTransition -> context.destinationJiraClient().transition(destinationKey, jiraTransition));
	}

	protected void updateIssueBody(JiraIssue sourceIssue, String destinationKey) {
		JiraIssue issue = issueToCreate(sourceIssue);

		updateIssue(destinationKey, issue, sourceIssue, context.notMappedAssignee());
	}

	protected void updateIssue(String destinationKey, JiraIssue issue, JiraIssue sourceIssue, JiraUser assignee) {
		try {
			context.destinationJiraClient().update(destinationKey, issue);
		} catch (JiraRestException e) {
			if (issue.fields.assignee == null) {
				// if we failed with no assignee then there's no point in retrying ...
				throw e;
			}
			if (e.getMessage().contains("\"assignee\"")) {
				// let's try updating with the assignee passed to the method (it may be the
				// "notMappedAssignee")
				failureCollector.warning(
						"Unable to update issue %s with assignee %s, will try to update one more time with assignee %s."
								.formatted(sourceIssue.key, issue.fields.assignee, assignee),
						e);
				issue.fields.assignee = assignee;
				updateIssue(destinationKey, issue, sourceIssue, null);
			} else {
				throw e;
			}
		}
	}

	protected JiraRemoteLink remoteSelfLink(JiraIssue sourceIssue) {
		URI jiraLink = createJiraIssueUri(sourceIssue);

		JiraRemoteLink link = new JiraRemoteLink();
		// >> Setting this field enables the remote issue link details to be updated or
		// deleted using remote system
		// >> and item details as the record identifier, rather than using the record's
		// Jira ID.
		//
		// Hence, we set this global id as a link to the issue, this way it should be
		// unique enough and easy to create:
		link.globalId = jiraLink.toString();
		link.relationship = "Upstream issue";
		link.object.title = sourceIssue.key;
		link.object.url = jiraLink;
		link.object.summary = "Link to an upstream JIRA issue, from which this one was cloned from.";

		return link;
	}

	protected JiraIssue issueToCreate(JiraIssue sourceIssue) {
		JiraIssue destinationIssue = new JiraIssue();
		destinationIssue.fields = new JiraFields();

		destinationIssue.fields.summary = sourceIssue.fields.summary;
		destinationIssue.fields.description = sourceIssue.fields.description;
		destinationIssue.fields.description = "%s%s".formatted(prepareDescriptionQuote(sourceIssue),
				Objects.toString(sourceIssue.fields.description, ""));
		destinationIssue.fields.description = truncateContent(destinationIssue.fields.description);

		destinationIssue.fields.labels = sourceIssue.fields.labels;
		// let's also add fix versions to the labels
		if (sourceIssue.fields.fixVersions != null) {
			if (destinationIssue.fields.labels == null) {
				destinationIssue.fields.labels = List.of();
			}
			destinationIssue.fields.labels = new ArrayList<>(destinationIssue.fields.labels);
			for (JiraSimpleObject fixVersion : sourceIssue.fields.fixVersions) {
				destinationIssue.fields.labels.add("Fix version:%s".formatted(fixVersion.name).replace(' ', '_'));
			}
		}

		// if we can map the priority - great we'll do that, if no: we'll keep it blank
		// and let Jira use its default instead:
		destinationIssue.fields.priority = sourceIssue.fields.priority != null
				? priority(sourceIssue.fields.priority.id).map(JiraSimpleObject::new).orElse(null)
				: null;

		destinationIssue.fields.project.id = context.project().projectId();

		destinationIssue.fields.issuetype = issueType(sourceIssue.fields.issuetype.id).map(JiraSimpleObject::new)
				.orElse(null);

		// now let's handle the users. we will consider only a mapped subset of users
		// and for other's the defaults will be used.
		// also the description is going to include a section mentioning who created and
		// who the issue is assigned to...
		if (context.projectGroup().canSetReporter()) {
			destinationIssue.fields.reporter = user(sourceIssue.fields.reporter).map(this::toUser).orElse(null);
		}

		if (sourceIssue.fields.assignee != null) {
			destinationIssue.fields.assignee = user(sourceIssue.fields.assignee).map(this::toUser)
					.orElseGet(context::notMappedAssignee);
		} else {
			// Because not sending an assignee just does not update it:
			// See also
			// https://confluence.atlassian.com/jirakb/how-to-set-assignee-to-unassigned-via-rest-api-in-jira-744721880.html
			destinationIssue.fields.assignee = JiraUser.unassigned(context.projectGroup().users().mappedPropertyName());
		}

		return destinationIssue;
	}

	private JiraUser toUser(String value) {
		return new JiraUser(context.projectGroup().users().mappedPropertyName(), value);
	}

	private Optional<JiraTransition> prepareTransition(JiraIssue sourceIssue) {
		return statusToTransition(sourceIssue.fields.status.id).map(JiraTransition::new);
	}

	protected Optional<JiraIssueLink> prepareParentLink(String destinationKey, JiraIssue sourceIssue) {
		if (sourceIssue.fields.parent != null) {
			String parent = toDestinationKey(sourceIssue.fields.parent.key);
			// we don't really need it, but as usual we are making sure that the issue is
			// available downstream:
			context.createNextPlaceholderBatch(parent);
			JiraIssueLink link = new JiraIssueLink();
			link.type.id = context.projectGroup().issueLinkTypes().parentLinkType();
			// "name": "Depend",
			// "inward": "is depended on by",
			// "outward": "depends on",
			//
			// TODO: Jira is sending a relates-to link created hook on its own
			// and it has the inward/outward sides opposite to what we do here
			// Let's double check what will happen with actual downstream jira
			// (there a depends on link should be created along side the one triggered
			// automatically by the source JIRA).
			link.inwardIssue.key = destinationKey;
			link.outwardIssue.key = parent;
			return Optional.of(link);
		} else {
			return Optional.empty();
		}
	}

	private String prepareDescriptionQuote(JiraIssue issue) {
		URI issueUri = createJiraIssueUri(issue);

		UserData assignee = userData(issue.self, issue.fields.assignee);
		UserData reporter = userData(issue.self, issue.fields.reporter);

		return """
				{quote}This issue is created as a copy of [%s|%s].

				Assigned to: %s.

				Reported by: %s.

				Upstream status: %s.{quote}


				""".formatted(issue.key, issueUri,
				assignee == null ? " Unassigned" : "[%s|%s]".formatted(assignee.name(), assignee.uri()),
				reporter == null ? " Unknown" : "[%s|%s]".formatted(reporter.name(), reporter.uri()),
				issue.fields.status != null ? issue.fields.status.name : "Unknown");
	}

}
