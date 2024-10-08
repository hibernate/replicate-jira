package org.hibernate.infra.sync.jira.service.jira.model.rest;

import java.util.List;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraIssueBulkResponse extends JiraBaseObject {
	public List<JiraIssueResponse> issues;

	@Override
	public String toString() {
		return "JiraIssueBulkResponse{" + "issues=" + issues + ", otherProperties=" + properties() + '}';
	}
}
