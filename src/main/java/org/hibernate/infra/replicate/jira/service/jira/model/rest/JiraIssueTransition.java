package org.hibernate.infra.replicate.jira.service.jira.model.rest;

public class JiraIssueTransition extends JiraSimpleObject {
	public JiraSimpleObject to;

	public JiraIssueTransition() {
	}

	public JiraIssueTransition(String id) {
		this.id = id;
	}
}
