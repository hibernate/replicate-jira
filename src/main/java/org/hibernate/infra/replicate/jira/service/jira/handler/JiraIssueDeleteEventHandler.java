package org.hibernate.infra.replicate.jira.service.jira.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraTransition;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraIssueDeleteEventHandler extends JiraIssueAbstractEventHandler {
	private final String key;

	public JiraIssueDeleteEventHandler(ReportingConfig reportingConfig, HandlerProjectGroupContext context, Long id,
			String key) {
		super(reportingConfig, context, id);
		this.key = key;
	}

	@Override
	protected void doRun() {
		// TODO: do we actually want to delete the issue ? or maybe let's instead add a
		// label, and update the summary, saying that the issue is deleted:

		// first let's make sure that the issue is actually deleted upstream:
		try {
			// Note: we do the search based on the jira key as we want to make sure
			// that such key does not exist, searching by ID may also not find the issue,
			// but then if the issue is not there we cannot check that the key matches the
			// ID.
			JiraIssue issue = context.sourceJiraClient().getIssue(key);
			if (issue != null && !key.equals(issue.key)) {
				// means the issue got moved:
				handleDeletedMovedIssue("MOVED (to %s)".formatted(issue.key));
				return;
			}

			// if the issue is deleted then we should get a 404 and never reach this line:
			failureCollector.critical("Request to delete an issue %s that is actually not deleted".formatted(key));
		} catch (JiraRestException e) {
			// all good the issue is not available let's mark the other one as deleted now:
			handleDeletedMovedIssue("DELETED");
		}
	}

	private void handleDeletedMovedIssue(String type) {
		try {
			String destinationKey = context.contextForOriginalProjectKey(toProjectFromKey(key)).toDestinationKey(key);
			JiraIssue issue = context.destinationJiraClient().getIssue(destinationKey);
			JiraIssue updated = new JiraIssue();

			updated.fields = new JiraFields();
			updated.fields.summary = "%s upstream: %s".formatted(type, issue.fields.summary);
			if (issue.fields.labels == null) {
				issue.fields.labels = List.of();
			}
			Set<String> updatedLabels = new HashSet<>(issue.fields.labels);
			updatedLabels.add("deleted_upstream");
			updated.fields.labels = new ArrayList<>(updatedLabels);
			updated.fields.priority = null;
			updated.fields.issuetype = null;
			updated.fields.project = null;

			context.destinationJiraClient().update(destinationKey, updated);

			prepareTransition(issue)
					.ifPresent(transition -> context.destinationJiraClient().transition(destinationKey, transition));

			context.destinationJiraClient().archive(destinationKey);
		} catch (Exception ex) {
			failureCollector.critical("Unable to mark the issue %s as deleted: %s".formatted(objectId, ex.getMessage()),
					ex);
		}
	}

	private Optional<JiraTransition> prepareTransition(JiraIssue issue) {
		Optional<String> deletedStatus = context.projectGroup().statuses().deletedStatus();
		if (deletedStatus.isPresent()) {
			prepareTransition(deletedStatus.get(), issue);

			Optional<String> deletedResolution = context.projectGroup().statuses().deletedResolution();
			JiraTransition transition = new JiraTransition(deletedStatus.get(), deletedResolution.orElse(null));

			return Optional.of(transition);
		}

		return Optional.empty();
	}

	@Override
	public String toString() {
		return "JiraIssueDeleteEventHandler[" + "key='" + key + '\'' + ", objectId=" + objectId + ", projectGroup="
				+ context.projectGroupName() + ']';
	}
}
