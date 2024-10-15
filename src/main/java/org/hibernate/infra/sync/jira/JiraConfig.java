package org.hibernate.infra.sync.jira;

import java.net.URI;
import java.util.Base64;
import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "jira")
public interface JiraConfig {

	Map<String, JiraProjectGroup> projectGroup();

	interface JiraProjectGroup {
		Instance source();

		Instance destination();

		UserValueMapping users();

		ValueMapping priorities();

		IssueLinkTypeValueMapping issueLinkTypes();

		ValueMapping statuses();

		ValueMapping issueTypes();

		@WithDefault("false")
		boolean canSetReporter();

		Map<String, JiraProject> projects();

		Scheduled scheduled();

		EventProcessing processing();
	}

	interface EventProcessing {
		@WithDefault("5")
		int eventsPerTimeframe();

		@WithDefault("2")
		int timeframeInSeconds();
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

		WebHookSecurity security();
	}

	interface WebHookSecurity {
		@WithDefault("false")
		boolean enabled();

		@WithDefault("not-a-secret")
		String secret();
	}

	interface Instance {
		@WithDefault("BASIC")
		LoginKind loginKind();

		JiraUser apiUser();

		URI apiUri();

		@WithDefault("false")
		boolean logRequests();
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
		 * @return the value of the issue link type id to use for linking a "sub-task"
		 *         ticket to a "parent" NOTE: Since changing issue to a subtask doesn't
		 *         work through the REST API we are creating a regular task instead and
		 *         adding an extra link for it.
		 */
		String parentLinkType();
	}

	interface UserValueMapping extends ValueMapping {
		/**
		 * @return the name of the property to apply the assignee value to. With Jira
		 *         Server the required property is {@code name}, while for the Cloud an
		 *         {@code accountId} is expected.
		 */
		@WithDefault("accountId")
		String mappedPropertyName();
	}

	/**
	 * Some JIRA instances may allow PAT logins (personal authentication tokens)
	 * while others: basic authentication with a username password/token
	 */
	enum LoginKind {
		/**
		 * Basic authentication with a username password/token. A string
		 * {@code username:password} is {@code base64}-encoded and passed in the auth
		 * header.
		 */
		BASIC {
			@Override
			public Map<String, String> headers(String username, String token) {
				return Map.of("Authorization", "Basic %s".formatted(
						Base64.getEncoder().encodeToString(("%s:%s".formatted(username, token)).getBytes())));
			}
		},
		/**
		 * A PAT login, where a JIRA generated token is simply passed as is in the
		 * header without any additional encoding, usernames or anything else. See also
		 * <a href=
		 * "https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html">personal
		 * access tokens</a>
		 */
		BEARER_TOKEN {
			@Override
			public Map<String, String> headers(String username, String token) {
				return Map.of("Authorization", "Bearer %s".formatted(token));
			}
		};

		public abstract Map<String, String> headers(String username, String token);
	}
}
