package org.hibernate.infra.replicate.jira.service.jira.model.hook;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.handler.action.JiraAssigneeActionEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public enum JiraActionEventType {
	ISSUE_ASSIGNED("jira:issue_assigned") {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraActionEvent event,
				HandlerProjectContext context) {
			if (event.assignee == null || event.key == null) {
				throw new IllegalStateException(
						"Trying to handle an issue event but issue id is null: %s".formatted(event));
			}
			return List.of(new JiraAssigneeActionEventHandler(reportingConfig, context, event));
		}
	},
	ISSUE_TRANSITIONED("jira:issue_transitioned") {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraActionEvent event,
				HandlerProjectContext context) {
			throw new UnsupportedOperationException("jira:issue_transitioned not supported yet");
		}
	};

	private final String name;

	JiraActionEventType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public static Optional<JiraActionEventType> of(String webhookEvent) {
		if (webhookEvent == null) {
			return Optional.empty();
		}
		for (JiraActionEventType value : values()) {
			if (value.name.equals(webhookEvent.toLowerCase(Locale.ROOT))) {
				return Optional.of(value);
			}
		}
		return Optional.empty();
	}

	public abstract Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraActionEvent event,
			HandlerProjectContext context);
}
