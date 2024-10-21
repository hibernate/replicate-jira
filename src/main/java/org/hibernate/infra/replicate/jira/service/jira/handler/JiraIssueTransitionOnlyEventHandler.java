package org.hibernate.infra.replicate.jira.service.jira.handler;

import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraTransition;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraIssueTransitionOnlyEventHandler extends JiraEventHandler {

	private final JiraIssue sourceIssue;

	public JiraIssueTransitionOnlyEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context,
			JiraIssue issue) {
		super(reportingConfig, context, issue.id);
		this.sourceIssue = issue;
	}

	@Override
	protected void doRun() {
		// NOTE: we do not look up the source issue as we've already queried for it
		// before creating this handler:
		String destinationKey = toDestinationKey(sourceIssue.key);
		// We don't really need one, but doing so means that we will create the
		// placeholder for it if the issue wasn't already present in the destination
		// Jira instance
		context.createNextPlaceholderBatch(destinationKey);

		try {
			// issue status can be updated only through transition:
			prepareTransition(sourceIssue).ifPresent(
					jiraTransition -> context.destinationJiraClient().transition(destinationKey, jiraTransition));
		} catch (JiraRestException e) {
			failureCollector
					.critical("Unable to update destination issue %s: %s".formatted(destinationKey, e.getMessage()), e);
		}
	}

	@Override
	public String toString() {
		return "JiraIssueTransitionOnlyEventHandler[" + "objectId=" + objectId + ", project=" + context.projectName()
				+ ']';
	}

	private Optional<JiraTransition> prepareTransition(JiraIssue sourceIssue) {
		return statusToTransition(sourceIssue.fields.status.id).map(
				tr -> new JiraTransition(tr, "Upstream issue status updated to: " + sourceIssue.fields.status.name));
	}

}
