package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.util.List;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraIssues extends JiraBaseObject {

	public List<JiraIssue> issues;
	public int startAt;
	public int maxResults;
	public int total;

}
