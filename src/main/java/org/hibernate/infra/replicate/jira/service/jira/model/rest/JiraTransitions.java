package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.util.List;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraTransitions extends JiraBaseObject {
	public List<JiraIssueTransition> transitions;
}
