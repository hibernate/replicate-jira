package org.hibernate.infra.sync.jira.service.jira.model.hook;

import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraSimpleObject;

public class JiraWebHookIssueLink extends JiraWebHookObject {

	public Long sourceIssueId;
	public Long destinationIssueId;
	public JiraSimpleObject issueLinkType;

}
