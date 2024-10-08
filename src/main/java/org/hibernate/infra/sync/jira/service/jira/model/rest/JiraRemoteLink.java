package org.hibernate.infra.sync.jira.service.jira.model.rest;

import java.net.URI;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraRemoteLink extends JiraBaseObject {
	public String globalId;
	public URI self;
	public String relationship;
	public LinkObject object = new LinkObject();

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
}
