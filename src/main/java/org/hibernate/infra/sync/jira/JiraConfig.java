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

		IssueLinkTypeValueMapping issueLinkTypes();

		ValueMapping statuses();

		ValueMapping issueTypes();

		@WithDefault("false")
		boolean canSetReporter();

		Map<String, JiraProject> projects();

		Scheduled scheduled();

	}

	interface Scheduled {
		String cron();

		// look for issues updated since "yesterday"
		@WithDefault("-1d")
		String timeFilter();
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

	interface IssueLinkTypeValueMapping extends ValueMapping {
		/**
		 * @return the value of the issue link type id to use for linking a "sub-task" ticket to a "parent"
		 * NOTE: Since changing issue to a subtask doesn't work through the REST API we are creating a regular
		 * task instead and adding an extra link for it.
		 */
		String parentLinkType();

	}
}
