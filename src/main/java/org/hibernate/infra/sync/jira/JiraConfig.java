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

		ValueMapping users();

		ValueMapping priorities();

		ValueMapping issueLinkTypes();

		ValueMapping statuses();

		ValueMapping issueTypes();

		@WithDefault("false")
		boolean canSetReporter();

		Map<String, JiraProject> projects();

	}

	interface JiraProject {
		String projectId();

		String projectKey();

		String originalProjectKey();
	}

	interface Instance {
		JiraUser apiUser();

		URI apiUri();
	}

	interface JiraUser {
		String email();

		String token();
	}

	interface ValueMapping {
		/**
		 * @return default value for the downstream JIRA.
		 */
		String defaultValue();

		Map<String, String> mapping();

	}
}
