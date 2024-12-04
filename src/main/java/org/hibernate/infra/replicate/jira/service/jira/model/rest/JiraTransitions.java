package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestClient;
import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;
import org.hibernate.infra.replicate.jira.service.reporting.FailureCollector;

public class JiraTransitions extends JiraBaseObject {
	public List<JiraIssueTransition> transitions;

	public static Optional<String> findRequiredTransitionId(JiraRestClient client, FailureCollector failureCollector,
			String status, JiraIssue issue) {
		if (status != null) {
			List<JiraIssueTransition> jiraTransitions = null;
			try {
				JiraTransitions transitions = client.availableTransitions(issue.key);
				jiraTransitions = transitions.transitions;
			} catch (Exception e) {
				failureCollector.warning("Failed to find a transition for %s".formatted(issue.key), e);
				jiraTransitions = Collections.emptyList();
			}
			for (JiraIssueTransition transition : jiraTransitions) {
				if (transition.to != null && status.equalsIgnoreCase(transition.to.name)) {
					return Optional.of(transition.id);
				}
			}
		}

		return Optional.empty();
	}
}
