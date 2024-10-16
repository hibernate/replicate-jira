package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.util.Collections;
import java.util.List;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraIssueBulk extends JiraBaseObject {
	public List<JiraIssue> issueUpdates;

	public JiraIssueBulk() {
	}

	public JiraIssueBulk(List<JiraIssue> issueUpdates) {
		this.issueUpdates = issueUpdates;
	}

	public JiraIssueBulk(JiraIssue placeholder, int times) {
		this.issueUpdates = Collections.nCopies(times, placeholder);
	}

	@Override
	public String toString() {
		return "JiraIssueBulk{" + "issueUpdates=" + issueUpdates + '}';
	}
}
