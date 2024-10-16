package org.hibernate.infra.replicate.jira.service.jira.client;

public class JiraRestException extends RuntimeException {
	private final int statusCode;

	public JiraRestException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
