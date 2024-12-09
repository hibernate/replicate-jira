package org.hibernate.infra.replicate.jira.service.jira.model.hook;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.handler.action.JiraAffectsVersionActionEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.handler.action.JiraAssigneeActionEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.handler.action.JiraFixVersionActionEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.handler.action.JiraTransitionActionEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public enum JiraActionEventType {
	ISSUE_ASSIGNED("jira:issue_update_assignee") {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraActionEvent event,
				HandlerProjectGroupContext context) {
			if (event.assignee == null || event.key == null) {
				throw new IllegalStateException(
						"Trying to handle an issue event but issue id is null: %s".formatted(event));
			}
			return List.of(new JiraAssigneeActionEventHandler(reportingConfig, context, event));
		}
	},
	ISSUE_TRANSITIONED("jira:issue_update_status") {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraActionEvent event,
				HandlerProjectGroupContext context) {
			return List.of(new JiraTransitionActionEventHandler(reportingConfig, context, event));
		}
	},
	FIX_VERSION_CHANGED("jira:issue_update_fixversion") {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraActionEvent event,
				HandlerProjectGroupContext context) {
			return List.of(new JiraFixVersionActionEventHandler(reportingConfig, context, event));
		}
	},
	AFFECTS_VERSION_CHANGED("jira:issue_update_version") {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraActionEvent event,
				HandlerProjectGroupContext context) {
			return List.of(new JiraAffectsVersionActionEventHandler(reportingConfig, context, event));
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
			if (value.name.equals(webhookEvent.toLowerCase(Locale.ROOT).replace(" ", ""))) {
				return Optional.of(value);
			}
		}
		return Optional.empty();
	}

	public abstract Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraActionEvent event,
			HandlerProjectGroupContext context);
}
