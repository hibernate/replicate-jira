package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraRemoteLink extends JiraBaseObject {
	public String globalId;
	public URI self;
	public String relationship;
	public LinkObject object = new LinkObject();
	public Application application;

	@Override
	public String toString() {
		return "JiraRemoteLink{" + "globalId='" + globalId + '\'' + ", self=" + self + ", relationship='" + relationship
				+ '\'' + ", object=" + object + ", otherProperties=" + properties() + '}';
	}

	public static class LinkObject extends JiraBaseObject {
		public String summary;
		public String title;
		public URI url;

		@Override
		public String toString() {
			return "LinkObject{" + "summary='" + summary + '\'' + ", title='" + title + '\'' + ", url=" + url + '}';
		}
	}

	public static class Application extends JiraBaseObject {
		public String name;
		public String type;

		public Application() {
		}

		public Application(String name) {
			this(name, "com.atlassian.jira");
		}

		public Application(String name, String type) {
			this.name = name;
			this.type = type;
		}
	}
}
