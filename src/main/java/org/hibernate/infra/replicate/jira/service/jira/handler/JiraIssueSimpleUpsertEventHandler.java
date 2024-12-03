package org.hibernate.infra.replicate.jira.service.jira.handler;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraIssueSimpleUpsertEventHandler extends JiraIssueInternalAbstractEventHandler {

	private final boolean applyTransitionUpdate;

	public JiraIssueSimpleUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context,
			JiraIssue issue) {
		this(reportingConfig, context, issue, false);
	}

	public JiraIssueSimpleUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context,
			JiraIssue issue, boolean applyTransitionUpdate) {
		super(reportingConfig, context, issue);
		this.applyTransitionUpdate = applyTransitionUpdate;
	}

	@Override
	protected void updateAction(String destinationKey, JiraIssue sourceIssue) {
		JiraIssue destIssue = context.destinationJiraClient().getIssue(destinationKey);
		updateIssueBody(sourceIssue, destIssue, destinationKey);
		if (applyTransitionUpdate) {
			applyTransition(sourceIssue, destIssue, destinationKey);
		}
	}

	@Override
	public String toString() {
		return "JiraIssueSimpleUpsertEventHandler[" + "objectId=" + objectId + ", project=" + context.projectName()
				+ ']';
	}
}
