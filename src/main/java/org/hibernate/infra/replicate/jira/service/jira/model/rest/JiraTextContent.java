package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraTextContent extends JiraBaseObject {
	public String type;

	public JiraTextContent() {
	}

	public JiraTextContent(String type, List<? extends JiraTextContent> content) {
		this.type = type;
		this.properties().put("content", content);
	}

	public JiraTextContent(String type, Map<String, Object> content) {
		this.type = type;
		this.properties().putAll(content);
	}

	public static String userIdPart(JiraUser author) {
		String accountId = author.accountId;
		int index = accountId.indexOf(':');
		return index > 0 ? accountId.substring(index) : accountId;
	}

	public static Map<String, Object> linkContent(String text, URI link) {
		return Map.of("text", text, "marks", List.of(Map.of("type", "link", "attrs", Map.of("href", link))));
	}

	public static Map<String, Object> simpleText(String text) {
		return Map.of("text", text);
	}
}
