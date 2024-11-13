package org.hibernate.infra.replicate.jira.service.jira.handler;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraIssueUpsertEventHandler extends JiraIssueAbstractEventHandler {

	public JiraIssueUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id) {
		super(reportingConfig, context, id);
	}

	@Override
	protected void doRun() {
		JiraIssue sourceIssue = null;
		try {
			sourceIssue = context.sourceJiraClient().getIssue(objectId);
		} catch (JiraRestException e) {
			failureCollector.critical("Source issue %d was not found through the REST API".formatted(objectId), e);
			// no point in continuing anything
			return;
		}

		String destinationKey = toDestinationKey(sourceIssue.key);
		// We don't really need one, but doing so means that we will create the
		// placeholder for it
		// if the issue wasn't already present in the destination Jira instance
		context.createNextPlaceholderBatch(destinationKey);

		try {
			JiraIssue destIssue = context.destinationJiraClient().getIssue(destinationKey);

			updateIssueBody(sourceIssue, destIssue, destinationKey);
			// remote "aka web" links cannot be added in the same request and are also not
			// returned as part of the issue API.
			// We "upsert" the remote link pointing to the "original/source" issue that
			// triggered the sync with an additional call:
			context.destinationJiraClient().upsertRemoteLink(destinationKey, remoteSelfLink(sourceIssue));
			// issue status can be updated only through transition:
			applyTransition(sourceIssue, destIssue, destinationKey);
			// and then we want to add a link to a parent, if the issue was actually a
			// sub-task which we've created as a task:
			prepareParentLink(destinationKey, sourceIssue).ifPresent(context.destinationJiraClient()::upsertIssueLink);
		} catch (JiraRestException e) {
			failureCollector
					.critical("Unable to update destination issue %s: %s".formatted(destinationKey, e.getMessage()), e);
		}
	}

	@Override
	public String toString() {
		return "JiraIssueUpsertEventHandler[" + "objectId=" + objectId + ", project=" + context.projectName() + ']';
	}
}
