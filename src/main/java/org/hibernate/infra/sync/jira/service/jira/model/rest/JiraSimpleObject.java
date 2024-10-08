package org.hibernate.infra.sync.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraSimpleObject extends JiraBaseObject {
	public String id;
	public String name;
	public URI self;

	public JiraSimpleObject() {
	}

	public JiraSimpleObject(String id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return "SimpleObjectField{" + "id=" + id + ", name='" + name + '\'' + ", self=" + self + "otherProperties="
				+ properties() + '}';
	}
}
