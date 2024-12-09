package org.hibernate.infra.replicate.jira.service.jira.handler;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraProject;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraVersion;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraVersionUpsertEventHandler extends JiraEventHandler {

	public JiraVersionUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectGroupContext context, Long id) {
		super(reportingConfig, context, id);
	}

	@Override
	protected void doRun() {
		JiraVersion version = context.sourceJiraClient().version(objectId);
		JiraProject project = context.sourceJiraClient().project(version.projectId);
		context.findContextForOriginalProjectKey(project.key).get().fixVersion(version, true);
	}

	@Override
	public String toString() {
		return "JiraVersionUpsertEventHandler{" + "objectId=" + objectId + '}';
	}
}
