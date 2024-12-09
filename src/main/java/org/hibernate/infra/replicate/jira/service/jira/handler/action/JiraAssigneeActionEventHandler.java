package org.hibernate.infra.replicate.jira.service.jira.handler.action;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraUser;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraAssigneeActionEventHandler extends JiraActionEventHandler {

	public JiraAssigneeActionEventHandler(ReportingConfig reportingConfig, HandlerProjectGroupContext context,
			JiraActionEvent event) {
		super(reportingConfig, context, event);
	}

	@Override
	protected void doRun() {
		JiraIssue issue = context.destinationJiraClient().getIssue(event.key);

		JiraUser user = null;
		if (issue.fields.assignee != null) {
			String accountId = context.upstreamUser(
					issue.fields.assignee.mappedIdentifier(context.projectGroup().users().mappedPropertyName()));

			if (accountId != null) {
				user = new JiraUser(accountId);
			}
		} else {
			user = new JiraUser("-1");
		}
		if (user != null) {
			context.sourceJiraClient().assign(context.contextForProject(event.projectKey).toSourceKey(event.key), user);
		}
	}

	@Override
	public String toString() {
		return "JiraAssigneeActionEventHandler[" + "event=" + event + ", projectGroup=" + context.projectGroupName()
				+ ']';
	}
}
