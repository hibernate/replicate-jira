package org.hibernate.infra.replicate.jira.service.jira.handler.action;

import java.util.List;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraVersion;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraFixVersionActionEventHandler extends JiraAbstractVersionActionEventHandler {

	public JiraFixVersionActionEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context,
			JiraActionEvent event) {
		super(reportingConfig, context, event);
	}

	protected void setVersionList(JiraIssue issue, List<JiraVersion> versions) {
		issue.fields.fixVersions = versions;
	}

	protected List<JiraVersion> versionList(JiraIssue issue) {
		return issue.fields.fixVersions;
	}

}
