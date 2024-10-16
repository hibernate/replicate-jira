package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraIssue extends JiraBaseObject {
	public Long id;
	public String key;
	public URI self;
	public JiraFields fields;
	public JiraIssueTransition transition;

	public static Long keyToLong(String key) {
		return Long.parseLong(key.substring(key.lastIndexOf('-') + 1));
	}

	@Override
	public String toString() {
		return "JiraIssue{" + "id=" + id + ", key='" + key + '\'' + ", self=" + self + ", fields=" + fields
				+ ", otherProperties=" + properties() + '}';
	}
}
