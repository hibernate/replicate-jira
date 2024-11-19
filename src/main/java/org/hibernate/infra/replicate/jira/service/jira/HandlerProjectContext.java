package org.hibernate.infra.replicate.jira.service.jira;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.hibernate.infra.replicate.jira.JiraConfig;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestClient;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssueBulk;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssueBulkResponse;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssues;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraUser;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraVersion;

import io.quarkus.logging.Log;

public final class HandlerProjectContext implements AutoCloseable {

	// JIRA REST API creates upto 50 issues at a time:
	// https://developer.atlassian.com/cloud/jira/platform/rest/v2/api-group-issues/#api-rest-api-2-issue-bulk-post
	private static final int ISSUES_PER_REQUEST = 25;
	private static final String SYNC_ISSUE_PLACEHOLDER_SUMMARY = "Sync issue placeholder";
	private final ReentrantLock lock = new ReentrantLock();
	private final ReentrantLock versionLock = new ReentrantLock();

	private final String projectName;
	private final String projectGroupName;
	private final JiraRestClient sourceJiraClient;
	private final JiraRestClient destinationJiraClient;
	private final HandlerProjectGroupContext projectGroupContext;
	private final JiraConfig.JiraProject project;
	private final AtomicLong currentIssueKeyNumber;
	private final JiraIssueBulk bulk;

	private final String projectKeyWithDash;
	private final JiraUser notMappedAssignee;

	private final Map<String, HandlerProjectContext> allProjectsContextMap;
	private final Pattern sourceLabelPattern;
	private final DateTimeFormatter formatter;

	private final Map<String, JiraVersion> destFixVersions;

	public HandlerProjectContext(String projectName, String projectGroupName, JiraRestClient sourceJiraClient,
			JiraRestClient destinationJiraClient, HandlerProjectGroupContext projectGroupContext,
			Map<String, HandlerProjectContext> allProjectsContextMap) {
		this.projectName = projectName;
		this.projectGroupName = projectGroupName;
		this.sourceJiraClient = sourceJiraClient;
		this.destinationJiraClient = destinationJiraClient;
		this.projectGroupContext = projectGroupContext;
		this.project = projectGroup().projects().get(projectName());
		this.currentIssueKeyNumber = new AtomicLong(getCurrentLatestJiraIssueKeyNumber());
		this.bulk = new JiraIssueBulk(createIssuePlaceholder(), ISSUES_PER_REQUEST);
		this.projectKeyWithDash = "%s-".formatted(project.projectKey());

		this.notMappedAssignee = projectGroup().users().notMappedAssignee()
				.map(v -> new JiraUser(projectGroup().users().mappedPropertyName(), v)).orElse(null);

		this.allProjectsContextMap = allProjectsContextMap;
		this.sourceLabelPattern = Pattern
				.compile(projectGroupContext.projectGroup().formatting().labelTemplate().formatted(".+"));
		this.formatter = DateTimeFormatter.ofPattern(projectGroupContext.projectGroup().formatting().timestampFormat());

		this.destFixVersions = getAndCreateMissingCurrentFixVersions(project, projectGroupContext, sourceJiraClient,
				destinationJiraClient);
	}

	public JiraConfig.JiraProject project() {
		return project;
	}

	public String projectName() {
		return projectName;
	}

	public String projectGroupName() {
		return projectGroupName;
	}

	public JiraRestClient sourceJiraClient() {
		return sourceJiraClient;
	}

	public JiraRestClient destinationJiraClient() {
		return destinationJiraClient;
	}

	public JiraConfig.JiraProjectGroup projectGroup() {
		return projectGroupContext.projectGroup();
	}

	public AtomicLong currentIssueKeyNumber() {
		return currentIssueKeyNumber;
	}

	public Long getLargestSyncedJiraIssueKeyNumber() {
		JiraIssues issues = destinationJiraClient.find("project = %s and summary !~\"%s\" ORDER BY key DESC"
				.formatted(project.projectId(), SYNC_ISSUE_PLACEHOLDER_SUMMARY), 0, 1);
		if (issues.issues.isEmpty()) {
			return 0L;
		} else {
			return JiraIssue.keyToLong(issues.issues.get(0).key);
		}
	}

	public Optional<JiraIssue> getNextIssueToSync(Long latestSyncedJiraIssueKeyNumber) {
		String query;
		if (latestSyncedJiraIssueKeyNumber > 0) {
			query = "project = %s and key > %s-%s ORDER BY key ASC".formatted(project.originalProjectKey(),
					project.originalProjectKey(), latestSyncedJiraIssueKeyNumber);
		} else {
			query = "project = %s ORDER BY key ASC".formatted(project.originalProjectKey(),
					project.originalProjectKey());
		}
		JiraIssues issues = sourceJiraClient.find(query, 0, 1);
		if (issues.issues.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(issues.issues.get(0));
		}
	}

	private Long getCurrentLatestJiraIssueKeyNumber() {
		try {
			JiraIssues issues = destinationJiraClient
					.find("project = %s ORDER BY created DESC".formatted(project.projectId()), 0, 1);
			if (issues.issues.isEmpty()) {
				return 0L;
			} else {
				return JiraIssue.keyToLong(issues.issues.get(0).key);
			}
		} catch (Exception e) {
			Log.warn("Couldn't get the latest Jira issue key number", e);
			return -1L;
		}
	}

	public void createNextPlaceholderBatch(String upToKey) {
		// it only makes sense to do the bulk-create if we are requesting it for the
		// same project we are actually in.
		if (upToKey.startsWith(projectKeyWithDash)) {
			Long upToKeyNumber = JiraIssue.keyToLong(upToKey);
			createNextPlaceholderBatch(upToKeyNumber);
		}
	}

	public void createNextPlaceholderBatch(Long upToKeyNumber) {
		if (requiredIssueKeyNumberShouldBeAvailable(upToKeyNumber)) {
			return;
		}
		lock.lock();
		if (requiredIssueKeyNumberShouldBeAvailable(upToKeyNumber)) {
			return;
		}
		try {
			do {
				JiraIssueBulkResponse response = destinationJiraClient.create(bulk);
				response.issues.stream().mapToLong(i -> JiraIssue.keyToLong(i.key)).max()
						.ifPresent(currentIssueKeyNumber::set);
				Log.infof(
						"Created more sync placeholders for %s; Current latest Jira key number is %s while required key is %s",
						projectName, currentIssueKeyNumber.get(), upToKeyNumber);
			} while (currentIssueKeyNumber.get() < upToKeyNumber);
		} finally {
			lock.unlock();
		}
	}

	public void startProcessingEvent() throws InterruptedException {
		projectGroupContext.startProcessingEvent();
	}

	public JiraUser notMappedAssignee() {
		return notMappedAssignee;
	}

	private boolean requiredIssueKeyNumberShouldBeAvailable(Long key) {
		return currentIssueKeyNumber.get() >= key;
	}

	private JiraIssue createIssuePlaceholder() {
		JiraIssue placeholder = new JiraIssue();
		placeholder.fields = new JiraFields();
		placeholder.fields.summary = SYNC_ISSUE_PLACEHOLDER_SUMMARY;

		placeholder.fields.description = "This is a placeholder issue. It will be updated at a later point in time. DO NOT EDIT.";

		placeholder.fields.project.id = project().projectId();
		placeholder.fields.issuetype.id = projectGroup().issueTypes().defaultValue();

		// just to be sure that these are not sent as part of
		// the placeholder request to keep it as simple as it can be:
		placeholder.fields.reporter = null;
		placeholder.fields.assignee = null;
		placeholder.fields.priority = null;

		return placeholder;
	}

	@Override
	public String toString() {
		return "HandlerProjectContext[" + "projectName=" + projectName + ", " + "projectGroupName=" + projectGroupName
				+ ", " + "sourceJiraClient=" + sourceJiraClient + ", " + "destinationJiraClient="
				+ destinationJiraClient + ", " + "projectGroup=" + projectGroupContext.projectGroup() + ", "
				+ "currentIssueKeyNumber=" + currentIssueKeyNumber + ']';
	}

	@Override
	public void close() {
		projectGroupContext.close();
	}

	public int pendingEventsInCurrentContext() {
		return projectGroupContext.pendingEventsInCurrentContext();
	}

	public void submitTask(Runnable runnable) {
		projectGroupContext.submitTask(runnable);
	}

	public Optional<HandlerProjectContext> contextForProjectInSameGroup(String project) {
		if (!projectGroup().projects().containsKey(project)) {
			// different project group, don't bother
			return Optional.empty();
		}
		return Optional.ofNullable(allProjectsContextMap.get(project));
	}

	public boolean isSourceLabel(String label) {
		return sourceLabelPattern.matcher(label).matches();
	}

	public String formatTimestamp(ZonedDateTime time) {
		return time != null ? time.format(formatter) : "";
	}

	public JiraVersion fixVersion(JiraVersion version) {
		return fixVersion(version, false);
	}

	public JiraVersion fixVersion(JiraVersion version, boolean force) {
		if (!force) {
			JiraVersion v = destFixVersions.get(version.name);
			if (v != null) {
				return v;
			}
		}
		versionLock.lock();
		try {
			if (force) {
				return destFixVersions.compute(version.name, (name, current) -> upsert(project, projectGroupContext,
						destinationJiraClient, version, List.of()));
			} else {
				return destFixVersions.computeIfAbsent(version.name,
						name -> upsert(project, projectGroupContext, destinationJiraClient, version, List.of()));
			}
		} catch (Exception e) {
			Log.errorf(e,
					"Couldn't create a copy of the fix version %s, version will not be synced for a particular Jira ticket.",
					version.name);
			return null;
		} finally {
			versionLock.unlock();
		}
	}

	public void refreshFixVersions() {
		versionLock.lock();
		try {
			destFixVersions.clear();
			destFixVersions.putAll(getAndCreateMissingCurrentFixVersions(project, projectGroupContext, sourceJiraClient,
					destinationJiraClient));
		} finally {
			versionLock.unlock();
		}
	}

	private static Map<String, JiraVersion> getAndCreateMissingCurrentFixVersions(JiraConfig.JiraProject project,
			HandlerProjectGroupContext projectGroupContext, JiraRestClient sourceJiraClient,
			JiraRestClient destinationJiraClient) {
		Map<String, JiraVersion> result = new HashMap<>();

		try {
			List<JiraVersion> upstreamVersions = sourceJiraClient.versions(project.originalProjectKey());
			List<JiraVersion> downstreamVersions = destinationJiraClient.versions(project.projectKey());

			for (JiraVersion upstreamVersion : upstreamVersions) {
				JiraVersion downstreamVersion = upsert(project, projectGroupContext, destinationJiraClient,
						upstreamVersion, downstreamVersions);
				result.put(upstreamVersion.name, downstreamVersion);
			}
		} catch (Exception e) {
			Log.errorf(e, "Encountered a problem while building the fix version map for %s: %s", project.projectKey(),
					e.getMessage());
		}

		return result;
	}

	private static JiraVersion upsert(JiraConfig.JiraProject project, HandlerProjectGroupContext projectGroupContext,
			JiraRestClient jiraRestClient, JiraVersion upstreamVersion, List<JiraVersion> downstreamVersions) {
		Optional<JiraVersion> version = JiraVersion.findVersion(upstreamVersion.id, downstreamVersions);
		JiraVersion downstreamVersion = null;
		if (version.isEmpty()) {
			Log.infof("Creating a new fix version for project %s: %s", project.projectKey(), upstreamVersion.name);
			downstreamVersion = processJiraVersion(project, projectGroupContext, upstreamVersion,
					() -> jiraRestClient.create(upstreamVersion.copyForProject(project)));
		} else if (versionNeedsUpdate(upstreamVersion, version.get())) {
			Log.infof("Updating a fix version for project %s: %s", project.projectKey(), upstreamVersion.name);
			downstreamVersion = processJiraVersion(project, projectGroupContext, upstreamVersion,
					() -> jiraRestClient.update(version.get().id, upstreamVersion.copyForProject(project)));
		} else {
			downstreamVersion = version.get();
		}
		return downstreamVersion;
	}

	private static JiraVersion processJiraVersion(JiraConfig.JiraProject project,
			HandlerProjectGroupContext projectGroupContext, JiraVersion upstreamVersion, Supplier<JiraVersion> action) {
		try {
			projectGroupContext.startProcessingEvent();
			return action.get();
		} catch (InterruptedException e) {
			Log.error("Interrupted while trying to process fix version", e);
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			Log.errorf(e, "Ignoring fix version sync. Unable to process fix %s version for project %s: %s",
					upstreamVersion.name, project.projectKey(), e.getMessage());
		}
		return null;
	}

	private static boolean versionNeedsUpdate(JiraVersion upstreamVersion, JiraVersion downstreamVersion) {
		return !Objects.equals(upstreamVersion.name, downstreamVersion.name)
				|| !Objects.equals(upstreamVersion.prepareVersionDescriptionForCopy(), downstreamVersion.description)
				|| upstreamVersion.released != downstreamVersion.released
				|| !Objects.equals(upstreamVersion.releaseDate, downstreamVersion.releaseDate);
	}
}
