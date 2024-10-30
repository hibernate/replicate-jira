package org.hibernate.infra.replicate.jira.service.jira;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.hibernate.infra.replicate.jira.JiraConfig;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestClient;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssueBulk;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssueBulkResponse;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssues;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraUser;

import io.quarkus.logging.Log;

public final class HandlerProjectContext implements AutoCloseable {

	// JIRA REST API creates upto 50 issues at a time:
	// https://developer.atlassian.com/cloud/jira/platform/rest/v2/api-group-issues/#api-rest-api-2-issue-bulk-post
	private static final int ISSUES_PER_REQUEST = 25;
	private static final String SYNC_ISSUE_PLACEHOLDER_SUMMARY = "Sync issue placeholder";
	private final ReentrantLock lock = new ReentrantLock();

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
}
