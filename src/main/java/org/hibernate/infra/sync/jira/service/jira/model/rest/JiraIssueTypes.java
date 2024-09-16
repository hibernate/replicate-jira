package org.hibernate.infra.sync.jira.service.jira.model.rest;

import java.util.List;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraIssueTypes extends JiraBaseObject {

	public List<JiraSimpleObject> values;
	public int startAt;
	public int maxResults;
	public int total;

}
