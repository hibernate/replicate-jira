package org.hibernate.infra.replicate.jira.service.jira.handler.action;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraVersion;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

abstract class JiraAbstractVersionActionEventHandler extends JiraActionEventHandler {

	public JiraAbstractVersionActionEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context,
			JiraActionEvent event) {
		super(reportingConfig, context, event);
	}

	@Override
	protected void doRun() {
		JiraIssue issue = context.destinationJiraClient().getIssue(event.key);

		JiraIssue updated = new JiraIssue();
		updated.fields = JiraFields.empty();

		List<JiraVersion> versionList = versionList(issue);

		List<JiraVersion> versions;
		if (versionList != null) {
			versions = new ArrayList<>(versionList.size());
			for (JiraVersion ver : versionList) {
				JiraVersion version = new JiraVersion();
				version.name = ver.name;
				versions.add(version);
			}
		} else {
			versions = List.of();
		}

		setVersionList(updated, versions);

		context.sourceJiraClient().update(toSourceKey(event.key), updated);
	}

	protected abstract void setVersionList(JiraIssue issue, List<JiraVersion> versions);

	protected abstract List<JiraVersion> versionList(JiraIssue issue);

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[" + "event=" + event + ", project=" + context.projectName() + ']';
	}
}
