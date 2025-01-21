package org.hibernate.infra.replicate.jira.service.jira.handler.action;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.handler.JiraStaticFieldMappingCache;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraTransition;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraTransitions;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraTransitionActionEventHandler extends JiraActionEventHandler {

	public JiraTransitionActionEventHandler(ReportingConfig reportingConfig, HandlerProjectGroupContext context,
			JiraActionEvent event) {
		super(reportingConfig, context, event);
	}

	@Override
	protected void doRun() {
		String sourceKey = context.contextForProject(event.projectKey).toSourceKey(event.key);
		JiraIssue issue = context.destinationJiraClient().getIssue(event.key);
		JiraIssue sourceIssue = context.sourceJiraClient().getIssue(sourceKey);

		String statusDownstream = issue.fields.status.name.toLowerCase(Locale.ROOT);
		String statusCurrent = sourceIssue.fields.status.name.toLowerCase(Locale.ROOT);
		String resolutionDownstream = issue.fields.resolution != null
				? issue.fields.resolution.name.toLowerCase(Locale.ROOT)
				: null;

		if (context.projectGroup().statuses().ignoreTransitionCondition().getOrDefault(statusCurrent, Set.of())
				.contains(statusDownstream)) {
			return;
		}

		String statusNew = context.upstreamStatus(statusDownstream);
		String resolution = context.upstreamResolution(resolutionDownstream);

		prepareTransition(statusNew, resolution, sourceIssue)
				.ifPresent(jiraTransition -> context.sourceJiraClient().transition(sourceKey, jiraTransition));
	}

	protected Optional<JiraTransition> prepareTransition(String upstreamStatus, String resolution, JiraIssue issue) {
		return statusToTransition(issue.fields.status.name, upstreamStatus, () -> JiraTransitions
				.findRequiredTransitionId(context.sourceJiraClient(), failureCollector, upstreamStatus, issue))
				.map(id -> new JiraTransition(id, resolution));
	}

	protected Optional<String> statusToTransition(String from, String to, Supplier<Optional<String>> transitionFinder) {
		return Optional.ofNullable(JiraStaticFieldMappingCache.statusUpstream(context.projectGroupName(),
				"%s->%s".formatted(from, to), tk -> transitionFinder.get().orElse(null)));
	}

	@Override
	public String toString() {
		return "JiraTransitionActionEventHandler[" + "event=" + event + ", projectGroup=" + context.projectGroupName()
				+ ']';
	}
}
