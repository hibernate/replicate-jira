package org.hibernate.infra.sync.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraComment extends JiraBaseObject {

	public String id;
	public URI self;
	public JiraUser author = new JiraUser();
	public JiraTextBody body = new JiraTextBody();

}
