package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraIssueLink extends JiraBaseObject {
	public String id;
	public URI self;
	public JiraSimpleObject type = new JiraSimpleObject();
	public LinkObject inwardIssue = new LinkObject();
	public LinkObject outwardIssue = new LinkObject();

	public static class LinkObject extends JiraBaseObject {
		public String id;
		public String key;
		public URI self;

		@Override
		public String toString() {
			return "LinkObject{" + "id='" + id + '\'' + ", key='" + key + '\'' + ", self=" + self + '}';
		}
	}
}
