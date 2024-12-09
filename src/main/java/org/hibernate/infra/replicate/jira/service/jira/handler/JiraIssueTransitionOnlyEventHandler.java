package org.hibernate.infra.replicate.jira.service.jira.handler;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraIssueTransitionOnlyEventHandler extends JiraIssueInternalAbstractEventHandler {

	public JiraIssueTransitionOnlyEventHandler(ReportingConfig reportingConfig, HandlerProjectGroupContext context,
			JiraIssue issue) {
		super(reportingConfig, context, issue);
	}

	@Override
	protected void updateAction(HandlerProjectContext projectContext, String destinationKey, JiraIssue sourceIssue) {
		applyTransition(sourceIssue, destinationKey);
	}

	@Override
	public String toString() {
		return "JiraIssueTransitionOnlyEventHandler[" + "objectId=" + objectId + ", projectGroup="
				+ context.projectGroupName() + ']';
	}
}
