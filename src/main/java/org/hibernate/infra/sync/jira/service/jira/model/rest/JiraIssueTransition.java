package org.hibernate.infra.sync.jira.service.jira.model.rest;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraIssueTransition extends JiraBaseObject {
	public String id;

	public JiraIssueTransition() {
	}

	public JiraIssueTransition(String id) {
		this.id = id;
	}
}
