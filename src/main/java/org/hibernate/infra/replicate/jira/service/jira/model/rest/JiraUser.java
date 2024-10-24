package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

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

	public static JiraUser unassigned(String propertyName) {
		// {
		// "name": "-1"
		// }
		// Note: If the name is "-1" default assignee is used.
		//
		// Note: we do not send something like "name": null as that will get dropped
		// from the request and not sent at all.
		return new JiraUser(propertyName, "-1");
	}
}
