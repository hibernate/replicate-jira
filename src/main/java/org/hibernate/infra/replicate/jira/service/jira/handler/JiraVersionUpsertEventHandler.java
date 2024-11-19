package org.hibernate.infra.replicate.jira.service.jira.handler;

import java.util.List;
import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraVersion;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraVersionUpsertEventHandler extends JiraEventHandler {

	public JiraVersionUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id) {
		super(reportingConfig, context, id);
	}

	@Override
	protected void doRun() {
		JiraVersion version = context.sourceJiraClient().version(objectId);
		List<JiraVersion> downstreamVersions = context.destinationJiraClient().versions(context.project().projectKey());

		JiraVersion send = version.copyForProject(context.project());

		Optional<JiraVersion> found = JiraVersion.findVersion(version.id, downstreamVersions);
		if (found.isPresent()) {
			context.destinationJiraClient().update(found.get().id, send);
		} else {
			context.destinationJiraClient().create(send);
		}
	}

	@Override
	public String toString() {
		return "JiraVersionUpsertEventHandler{" + "objectId=" + objectId + '}';
	}
}
