package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraIssueResponse extends JiraBaseObject {
	public Long id;
	public String key;
	public URI self;

	@Override
	public String toString() {
		return "JiraIssue{" + "id=" + id + ", key='" + key + '\'' + ", self=" + self + ", otherProperties="
				+ properties() + '}';
	}
}
