package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraTransition extends JiraBaseObject {
	public JiraIssueTransition transition;
	public JiraUpdate update;
	public JiraTransitionFields fields;

	public JiraTransition() {
	}

	public JiraTransition(String transitionId) {
		this(transitionId, null, null);
	}

	public JiraTransition(String transitionId, String resolution) {
		this(transitionId, resolution, null);
	}

	public JiraTransition(String transitionId, String resolution, String comment) {
		transition = new JiraIssueTransition(transitionId);
		if (resolution != null) {
			fields = JiraTransitionFields.forResolution(resolution);
		}
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
