package org.hibernate.infra.sync.jira.service.jira;

import org.hibernate.infra.sync.jira.JiraConfig;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestClient;

public record HandlerProjectContext(
		String projectName, String projectGroupName, JiraRestClient sourceJiraClient,
		JiraRestClient destinationJiraClient, JiraConfig.JiraProjectGroup projectGroup
) {

	public JiraConfig.JiraProject project() {
		return projectGroup().projects().get( projectName() );
	}
}
