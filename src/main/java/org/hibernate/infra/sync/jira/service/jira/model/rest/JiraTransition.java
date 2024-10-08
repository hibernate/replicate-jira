package org.hibernate.infra.sync.jira.service.jira.model.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.infra.sync.jira.service.jira.model.JiraBaseObject;

public class JiraTransition extends JiraBaseObject {
	public JiraIssueTransition transition;
	public JiraUpdate update;

	public JiraTransition() {
	}

	public JiraTransition(String transitionId, String comment) {
		transition = new JiraIssueTransition(transitionId);
		if (comment != null && !comment.isBlank()) {
			update = new JiraUpdate();
			JiraBaseObject c = new JiraBaseObject();
			update.comment.add(c);
			c.properties().put("add", Map.of("body", comment));
		}
	}

	@Override
	public String toString() {
		return "JiraIssue{" + "transition=" + transition + ", update=" + update + ", otherProperties=" + properties()
				+ '}';
	}

	public static class JiraUpdate extends JiraBaseObject {
		public List<JiraBaseObject> comment = new ArrayList<>();
	}
}
