package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraProject extends JiraBaseObject {
	public String id;
	public String key;
	public URI self;

	public JiraProject() {
	}

	public JiraProject(String id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return "JiraProject{" + "id=" + id + ", key='" + key + '\'' + ", self=" + self + "otherProperties="
				+ properties() + '}';
	}
}
