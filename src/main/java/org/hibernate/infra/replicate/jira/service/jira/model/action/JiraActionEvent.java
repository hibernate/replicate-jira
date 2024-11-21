package org.hibernate.infra.replicate.jira.service.jira.model.action;

import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;
import org.hibernate.infra.replicate.jira.service.jira.model.hook.JiraActionEventType;

public class JiraActionEvent extends JiraBaseObject {
	public String id;
	public String key;
	public String event;
	public String assignee;
	public String status;

	public String triggeredByUser;

	public Optional<JiraActionEventType> eventType() {
		return JiraActionEventType.of(event);
	}

	@Override
	public String toString() {
		return "JiraActionEvent{" + "id='" + id + '\'' + ", key='" + key + '\'' + ", event='" + event + '\''
				+ ", assignee='" + assignee + '\'' + ", status='" + status + '\'' + '}';
	}
}
