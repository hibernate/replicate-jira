package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;
import java.time.ZonedDateTime;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraComment extends JiraBaseObject {

	public String id;
	public URI self;
	public JiraUser author = new JiraUser();
	public JiraUser updateAuthor;
	public String body;
	public ZonedDateTime created;
	public ZonedDateTime updated;

	public JiraComment() {
	}

	public JiraComment(String id) {
		this.id = id;
	}

	public boolean isUpdatedSameAsCreated() {
		return updated != null && updated.equals(created);
	}
}
