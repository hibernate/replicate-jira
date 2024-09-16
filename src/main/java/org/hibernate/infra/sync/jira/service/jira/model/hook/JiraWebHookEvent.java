package org.hibernate.infra.sync.jira.service.jira.model.hook;


import java.util.Optional;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

import com.fasterxml.jackson.annotation.JsonRootName;

/**
 * Note that for the objects in the payload we define only the very basic properties.
 * This is on purpose, since we "don't trust" the incoming data as it will come from an "open" endpoint (cannot secure a webhook).
 * Because of that, we will just take the id of an object and then get the data we need by calling REST API.
 */
@JsonRootName("payload")
public class JiraWebHookEvent extends JiraBaseObject {
	public String webhookEvent;
	public JiraWebHookObject comment;
	public JiraWebHookIssue issue;
	public JiraWebHookIssueLink issueLink;

	public Optional<JiraWebhookEventType> eventType() {
		return JiraWebhookEventType.of( webhookEvent );
	}

	@Override
	public String toString() {
		return "JiraWebHookEvent{" +
				"webhookEvent='" + webhookEvent + '\''
				+ ", comment=" + comment
				+ ", issue=" + issue
				+ ", otherProperties=" + properties()
				+ '}';
	}
}
