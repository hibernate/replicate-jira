package org.hibernate.infra.replicate.jira.service.jira.model.hook;

import java.net.URI;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraWebHookObject extends JiraBaseObject {
	public Long id;
	public URI self;

	@Override
	public String toString() {
		return "JiraWebHookObject{" + "id=" + id + ", self=" + self + ", otherProperties=" + properties() + '}';
	}
}
