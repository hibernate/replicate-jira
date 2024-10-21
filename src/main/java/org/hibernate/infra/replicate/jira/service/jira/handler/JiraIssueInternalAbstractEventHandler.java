package org.hibernate.infra.replicate.jira.service.jira.handler;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

abstract class JiraIssueInternalAbstractEventHandler extends JiraIssueAbstractEventHandler {

	private final JiraIssue sourceIssue;

	protected JiraIssueInternalAbstractEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context,
			JiraIssue issue) {
		super(reportingConfig, context, issue.id);
		this.sourceIssue = issue;
	}

	@Override
	protected final void doRun() {
		// NOTE: we do not look up the source issue as we've already queried for it
		// before creating this handler:
		String destinationKey = toDestinationKey(sourceIssue.key);
		// We don't really need one, but doing so means that we will create the
		// placeholder for it if the issue wasn't already present in the destination
		// Jira instance
		context.createNextPlaceholderBatch(destinationKey);

		try {
			updateAction(destinationKey, sourceIssue);
		} catch (JiraRestException e) {
			failureCollector
					.critical("Unable to update destination issue %s: %s".formatted(destinationKey, e.getMessage()), e);
		}
	}

	protected abstract void updateAction(String destinationKey, JiraIssue sourceIssue);

}