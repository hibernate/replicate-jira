package org.hibernate.infra.sync.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraIssue extends JiraBaseObject {
	public Long id;
	public String key;
	public URI self;
	public JiraFields fields;

	@Override
	public String toString() {
		return "JiraIssue{"
				+ "id=" + id
				+ ", key='" + key + '\''
				+ ", self=" + self
				+ ", fields=" + fields
				+ ", otherProperties=" + properties()
				+ '}';
	}
}
