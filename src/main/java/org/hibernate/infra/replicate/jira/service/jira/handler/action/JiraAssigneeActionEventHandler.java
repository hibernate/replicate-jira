package org.hibernate.infra.replicate.jira.service.jira.handler.action;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraUser;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraAssigneeActionEventHandler extends JiraActionEventHandler {

	public JiraAssigneeActionEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context,
			JiraActionEvent event) {
		super(reportingConfig, context, event);
	}

	@Override
	protected void doRun() {
		JiraIssue issue = context.destinationJiraClient().getIssue(event.key);

		JiraIssue updated = new JiraIssue();
		updated.fields = JiraFields.empty();
		if (issue.fields.assignee != null) {
			String accountId = context.upstreamUser(
					issue.fields.assignee.mappedIdentifier(context.projectGroup().users().mappedPropertyName()));

			if (accountId != null) {
				updated.fields.assignee = new JiraUser(accountId);

			}
		} else {
			updated.fields.assignee = new JiraUser("-1");
		}
		context.sourceJiraClient().update(toSourceKey(event.key), updated);
	}

	@Override
	public String toString() {
		return "JiraAssigneeActionEventHandler[" + "event=" + event + ", project=" + context.projectName() + ']';
	}
}
