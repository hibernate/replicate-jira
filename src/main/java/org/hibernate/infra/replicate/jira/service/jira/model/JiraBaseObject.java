package org.hibernate.infra.replicate.jira.service.jira.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class JiraBaseObject {
	// for any other properties that we don't really care about, but ... just in
	// case we want to check something while debugging:
	@JsonAnyGetter
	@JsonAnySetter
	private Map<String, Object> properties = new HashMap<>();

	public Map<String, Object> properties() {
		return properties;
	}

}
