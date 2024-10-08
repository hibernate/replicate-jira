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

	public JiraUser(String propertyName, String value) {
		if ("accountId".equals(propertyName)) {
			this.accountId = value;
		} else {
			properties().put(propertyName, value);
		}
	}
}
