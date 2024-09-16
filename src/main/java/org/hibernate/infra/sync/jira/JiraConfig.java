package org.hibernate.infra.sync.jira;

import java.net.URI;
import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "jira")
public interface JiraConfig {

	Map<String, JiraProjectGroup> projectGroup();

	interface JiraProjectGroup {
		Instance source();

		Instance destination();

		@WithDefault("3")
		String defaultIssueTypeId();

		@WithDefault("3")
		String defaultIssueLinkTypeId();

		Map<String, String> users();

		@WithDefault("false")
		boolean canSetReporter();

		Map<String, JiraProject> projects();

	}

	interface JiraProject {
		String projectId();
	}

	interface Instance {
		JiraUser apiUser();

		URI apiUri();
	}

	interface JiraUser {
		String email();

		String token();
	}
}
