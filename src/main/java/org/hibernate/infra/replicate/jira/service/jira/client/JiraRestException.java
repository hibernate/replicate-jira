package org.hibernate.infra.replicate.jira.service.jira.client;

import java.util.List;
import java.util.Map;

public class JiraRestException extends RuntimeException {
	private final int statusCode;
	private final Map<String, List<Object>> headers;

	public JiraRestException(String message, int statusCode, Map<String, List<Object>> headers) {
		super(message);
		this.statusCode = statusCode;
		this.headers = headers;
	}

	public int statusCode() {
		return statusCode;
	}

	public Map<String, List<Object>> headers() {
		return headers;
	}
}
