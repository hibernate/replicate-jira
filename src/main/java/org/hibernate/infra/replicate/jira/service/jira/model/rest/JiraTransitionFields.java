package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

public class JiraTransitionFields extends JiraBaseObject {

	public JiraSimpleObject resolution;

	public static JiraTransitionFields forResolution(String resolution) {
		if (resolution == null) {
			return null;
		}
		JiraTransitionFields fields = new JiraTransitionFields();
		fields.resolution = new JiraSimpleObject();
		fields.resolution.name = resolution;
		return fields;

	}

	@Override
	public String toString() {
		return "JiraTransitionFields{" + "resolution=" + resolution + "otherProperties=" + properties() + '}';
	}

}
