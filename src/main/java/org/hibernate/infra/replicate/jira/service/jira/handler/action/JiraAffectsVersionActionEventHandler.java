package org.hibernate.infra.replicate.jira.service.jira.handler.action;

import java.util.List;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraVersion;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraAffectsVersionActionEventHandler extends JiraAbstractVersionActionEventHandler {

	public JiraAffectsVersionActionEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context,
			JiraActionEvent event) {
		super(reportingConfig, context, event);
	}

	protected void setVersionList(JiraIssue issue, List<JiraVersion> versions) {
		issue.fields.versions = versions;
	}

	protected List<JiraVersion> versionList(JiraIssue issue) {
		return issue.fields.versions;
	}

}
