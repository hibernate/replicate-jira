package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraFields extends JiraBaseObject {

	public String summary;
	public String description;
	public JiraSimpleObject priority = new JiraSimpleObject();
	public JiraSimpleObject issuetype = new JiraSimpleObject();
	public JiraSimpleObject project = new JiraSimpleObject();
	public JiraSimpleObject status;
	public List<String> labels;

	public JiraUser assignee;
	public JiraUser reporter;
	public List<JiraSimpleObject> fixVersions;
	// NOTE: this one is for "read-only" purposes, to create links a different API
	// has to be used
	public List<JiraIssueLink> issuelinks;
	public JiraComments comment;
	public JiraIssue parent;
	public ZonedDateTime created;
	public ZonedDateTime updated;

	@Override
	public String toString() {
		return "JiraFields{" + "summary='" + summary + '\'' + ", description=" + description + ", priority=" + priority
				+ ", issuetype=" + issuetype + ", project=" + project + ", labels=" + labels + "otherProperties="
				+ properties() + '}';
	}

}
