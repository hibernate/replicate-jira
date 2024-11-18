package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraVersion extends JiraBaseObject {

	public URI self;
	public String id;
	public String name;
	public String description;
	public String projectId;

	public JiraVersion() {
	}

	public JiraVersion(String id) {
		this.id = id;
	}

}
