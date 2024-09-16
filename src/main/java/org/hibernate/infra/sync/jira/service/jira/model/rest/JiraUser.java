package org.hibernate.infra.sync.jira.service.jira.model.rest;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraUser extends JiraBaseObject {
	public String accountId;
	public String displayName;
	public String emailAddress;

	public JiraUser() {
	}

	public JiraUser(String accountId) {
		this.accountId = accountId;
	}
}
