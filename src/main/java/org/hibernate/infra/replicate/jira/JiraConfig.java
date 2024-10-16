package org.hibernate.infra.replicate.jira;

import java.net.URI;
import java.util.Base64;
import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "jira")
public interface JiraConfig {

	/**
	 * Describes the sync configuration of a project group. A number of projects
	 * that live in one Jira instance and is synced to another (single) Jira
	 * instance is considered a project group.
	 * <p>
	 * It's where all the mapping and connection details can be defined/customized.
	 */
	Map<String, JiraProjectGroup> projectGroup();

	interface JiraProjectGroup {
		/**
		 * Configuration of the source (upstream) Jira instance.
		 */
		Instance source();

		/**
		 * Configuration of the destination (downstream) Jira instance.
		 */
		Instance destination();

		/**
		 * Mapping of upstream users to downstream users. If a "not mapped user" is
		 * encountered while syncing the issue/comment then the Service Account will be
		 * set as the reporter, or "unassigned" for the assignee.
		 */
		UserValueMapping users();

		/**
		 * Mapping of upstream priorities to downstream ones. Please make sure to review
		 * your project scheme to see which priorities are available.
		 */
		ValueMapping priorities();

		/**
		 * Mapping of upstream issue link types to downstream ones. Please make sure to
		 * review your project scheme to see which issue link types are available.
		 */
		IssueLinkTypeValueMapping issueLinkTypes();

		/**
		 * Mapping of upstream statuses to downstream transitions. Please make sure to
		 * review your project scheme to see which transitions can be used to set the
		 * desired status.
		 */
		ValueMapping statuses();

		/**
		 * Mapping of upstream issue types to downstream ones. Please make sure to
		 * review your project scheme to see which issue types are available.
		 */
		ValueMapping issueTypes();

		/**
		 * Depending on your downstream JIRA permission schema configuration, Service
		 * Account may not have permissions to edit the reporter field. In that case
		 * keep the value as default {@code false}, which will result in reporter being
		 * set as the Service Account. Otherwise, if {@code true} is set, the tool will
		 * attempt to set the mapped user as a reporter in the update request.
		 */
		@WithDefault("false")
		boolean canSetReporter();

		Map<String, JiraProject> projects();

		/**
		 * If enabled, will run a cron job that takes latest updated tickets in the
		 * project group and "re-sync" them.
		 */
		Scheduled scheduled();

		/**
		 * Usually the Jira instance would have some request limits, look for
		 * {@code x-ratelimit-*} headers in the responses from the REST API.
		 * <p>
		 * To not overwhelm the Jira instance with requests, the number of events
		 * processed through a period of time can be limited. During each specified
		 * timeframe a certain number of events can be processed. If the number of
		 * events to process exceed the limit, the next event will get blocked waiting
		 * for the timeframe to end and will get process
		 */
		EventProcessing processing();
	}

	interface EventProcessing {
		/**
		 * Defines how many events can be processed within the
		 * {@link #timeframeInSeconds() timeframe}
		 */
		@WithDefault("5")
		int eventsPerTimeframe();

		/**
		 * Define the duration of the timeframe. Each timeframe will allow to precess
		 * the defined number of events and blocking any others to wait for the next
		 * timeframe.
		 */
		@WithDefault("2")
		int timeframeInSeconds();
	}

	interface Scheduled {
		/**
		 * Specify the cron string to define when the "re-sync" should be performed.
		 */
		String cron();

		/**
		 * Specify for how many tickets to look for. By default ({@code -1d} the tool
		 * will take the tickets updated during the last 24 hours. See JQL to learn more
		 * on possible query values for this filer.
		 */
		@WithDefault("-1d")
		String timeFilter();
	}

	interface JiraProject {
		/**
		 * Downstream project id (not a project key!).
		 * Use {@code rest/api/2/project/YOUR-PROJECT-ID} to get the info.
		 */
		String projectId();

		/**
		 * Downstream project key.
		 * Use {@code rest/api/2/project/YOUR-PROJECT-ID} to get the info.
		 */
		String projectKey();

		/**
		 * Upstream project key.
		 * Use {@code rest/api/2/project/YOUR-PROJECT-ID} to get the info.
		 */
		String originalProjectKey();

		/**
		 * Allows enabling signature verification.
		 */
		WebHookSecurity security();
	}

	interface WebHookSecurity {
		/**
		 * Whether to enable signature verification.
		 * <p>
		 * Jira web hooks can send a {@code x-hub-signature} header with a signature of a request body.
		 * This signature can be then verified using the secret used to configure the web hook.
		 */
		@WithDefault("false")
		boolean enabled();

		/**
		 * The secret used to sing the web hook request body.
		 */
		@WithDefault("not-a-secret")
		String secret();
	}

	interface Instance {
		@WithDefault("BASIC")
		LoginKind loginKind();

		JiraUser apiUser();

		URI apiUri();

		/**
		 * Whether to log request/response bodies of a Jira REST API calls.
		 */
		@WithDefault("false")
		boolean logRequests();
	}

	interface JiraUser {
		/**
		 * Service account email/username to use for basic authentication.
		 */
		String email();

		/**
		 * A personal authentication token for the service account.
		 */
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
