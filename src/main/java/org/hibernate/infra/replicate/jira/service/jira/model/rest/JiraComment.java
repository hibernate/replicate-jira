package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraComment extends JiraBaseObject {

	public String id;
	public URI self;
	public JiraUser author = new JiraUser();
	public String body;

	public JiraComment() {
	}

	public JiraComment(String id) {
		this.id = id;
	}
}
