package org.hibernate.infra.sync.jira.service.jira;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.hibernate.infra.sync.jira.JiraConfig;
import org.hibernate.infra.sync.jira.ProcessingConfig;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestClient;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestClientBuilder;
import org.hibernate.infra.sync.jira.service.jira.model.hook.JiraWebHookEvent;
import org.hibernate.infra.sync.jira.service.jira.model.hook.JiraWebHookIssue;
import org.hibernate.infra.sync.jira.service.jira.model.hook.JiraWebHookIssueLink;
import org.hibernate.infra.sync.jira.service.jira.model.hook.JiraWebHookObject;
import org.hibernate.infra.sync.jira.service.jira.model.hook.JiraWebhookEventType;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComment;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComments;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssueLink;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssues;
import org.hibernate.infra.sync.jira.service.reporting.FailureCollector;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.vertx.http.ManagementInterface;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
public class JiraService {

	private final ReportingConfig reportingConfig;
	private final ExecutorService executor;
	private final Supplier<Integer> workQueueSize;
	private final Map<String, HandlerProjectContext> contextPerProject;
	private final JiraConfig jiraConfig;
	private final Scheduler scheduler;

	@Inject
	public JiraService(ProcessingConfig processingConfig, JiraConfig jiraConfig, ReportingConfig reportingConfig,
			Scheduler scheduler) {
		LinkedBlockingDeque<Runnable> workQueue = new LinkedBlockingDeque<>(processingConfig.queueSize());
		workQueueSize = workQueue::size;
		executor = new ThreadPoolExecutor(processingConfig.threads(), processingConfig.threads(), 0L,
				TimeUnit.MILLISECONDS, workQueue);

		Map<String, HandlerProjectContext> contextMap = new HashMap<>();
		for (var entry : jiraConfig.projectGroup().entrySet()) {
			JiraRestClient source = JiraRestClientBuilder.of(entry.getValue().source());
			JiraRestClient destination = JiraRestClientBuilder.of(entry.getValue().destination());
			HandlerProjectGroupContext groupContext = new HandlerProjectGroupContext(entry.getValue());
			for (var project : entry.getValue().projects().entrySet()) {
				contextMap.put(project.getKey(),
						new HandlerProjectContext(project.getKey(), entry.getKey(), source, destination, groupContext));
			}
		}

		this.contextPerProject = Collections.unmodifiableMap(contextMap);
		this.reportingConfig = reportingConfig;

		configureScheduledTasks(scheduler, jiraConfig);
		this.jiraConfig = jiraConfig;
		this.scheduler = scheduler;
	}

	private void configureScheduledTasks(Scheduler scheduler, JiraConfig jiraConfig) {
		for (var entry : jiraConfig.projectGroup().entrySet()) {
			scheduler.newJob("Sync project group %s".formatted(entry.getKey()))
					.setCron(entry.getValue().scheduled().cron())
					.setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP).setTask(executionContext -> {
						syncLastUpdated(entry.getKey());
					}).schedule();
		}
	}

	public void registerManagementRoutes(@Observes ManagementInterface mi) {
		mi.router().get("/sync/issues/init").consumes(MediaType.APPLICATION_JSON).blockingHandler(rc -> {
			JsonObject request = rc.body().asJsonObject();
			String project = request.getString("project");

			HandlerProjectContext context = contextPerProject.get(project);

			if (context == null) {
				throw new IllegalArgumentException("Unknown project '%s'".formatted(project));
			}

			AtomicLong largestSyncedJiraIssueKeyNumber = new AtomicLong(context.getLargestSyncedJiraIssueKeyNumber());

			String identity = "Init Sync for project %s".formatted(project);
			scheduler.newJob(identity).setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
					// every 10 seconds:
					.setCron("0/10 * * * * ?").setTask(executionContext -> {
						Optional<JiraIssue> issueToSync = context
								.getNextIssueToSync(largestSyncedJiraIssueKeyNumber.get());
						if (issueToSync.isEmpty()) {
							scheduler.unscheduleJob(identity);
						} else {
							triggerSyncEvent(issueToSync.get(), context);
							largestSyncedJiraIssueKeyNumber.set(JiraIssue.keyToLong(issueToSync.get().key));
						}
					}).schedule();
			rc.end();
		});
		mi.router().get("/sync/issues/init/:project").blockingHandler(rc -> {
			// TODO: we can remove this one once we figure out why POST management does not
			// work correctly...
			String project = rc.pathParam("project");

			HandlerProjectContext context = contextPerProject.get(project);

			if (context == null) {
				throw new IllegalArgumentException("Unknown project '%s'".formatted(project));
			}

			AtomicLong largestSyncedJiraIssueKeyNumber = new AtomicLong(context.getLargestSyncedJiraIssueKeyNumber());

			String identity = "Init Sync for project %s".formatted(project);
			scheduler.newJob(identity).setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
					// every 10 seconds:
					.setCron("0/10 * * * * ?").setTask(executionContext -> {
						Optional<JiraIssue> issueToSync = context
								.getNextIssueToSync(largestSyncedJiraIssueKeyNumber.get());
						if (issueToSync.isEmpty()) {
							scheduler.unscheduleJob(identity);
						} else {
							triggerSyncEvent(issueToSync.get(), context);
							largestSyncedJiraIssueKeyNumber.set(JiraIssue.keyToLong(issueToSync.get().key));
						}
					}).schedule();
			rc.end();
		});
		mi.router().post("/sync/issues/list").consumes(MediaType.APPLICATION_JSON).blockingHandler(rc -> {
			// sync issues based on a list of issue-keys supplied in the JSON body:
			JsonObject request = rc.body().asJsonObject();
			String project = request.getString("project");
			List<String> issueKeys = request.getJsonArray("issues").stream().map(Objects::toString).toList();

			HandlerProjectContext context = contextPerProject.get(project);

			if (context == null) {
				throw new IllegalArgumentException("Unknown project '%s'".formatted(project));
			}

			executor.submit(() -> {
				for (String issueKey : issueKeys) {
					triggerSyncEvent(context.sourceJiraClient().getIssue(issueKey), context);
				}
			});
			rc.end();
		});
		mi.router().post("/sync/issues/query").consumes(MediaType.APPLICATION_JSON).blockingHandler(rc -> {
			JsonObject request = rc.body().asJsonObject();
			String project = request.getString("project");
			String query = request.getString("query");

			HandlerProjectContext context = contextPerProject.get(project);

			if (context == null) {
				throw new IllegalArgumentException("Unknown project '%s'".formatted(project));
			}

			executor.submit(() -> syncByQuery(query, context));
			rc.end();
		});
		mi.router().post("/sync/comments/list").consumes(MediaType.APPLICATION_JSON).blockingHandler(rc -> {
			JsonObject request = rc.body().asJsonObject();
			String project = request.getString("project");
			List<JiraComment> comments = request.getJsonArray("comments").stream().map(Objects::toString)
					.map(JiraComment::new).toList();

			HandlerProjectContext context = contextPerProject.get(project);

			if (context == null) {
				throw new IllegalArgumentException("Unknown project '%s'".formatted(project));
			}

			// TODO: needs an issue key
			// can trigger directly as we do not make any REST requests here:
			triggerCommentSyncEvents(project, null, comments);
			rc.end();
		});
	}

	/**
	 * At this point we want to only make some simple validation of the request
	 * body, and then add a request to the processing queue. We do not want to start
	 * doing actual processing before replying back to the Jira that we've received
	 * the event. It may be that processing will take some time, and it may result
	 * in a timeout on the hook side.
	 *
	 * @param project
	 *            The project key as defined in the
	 *            {@link org.hibernate.infra.sync.jira.JiraConfig.JiraProjectGroup#projects()}
	 * @param event
	 *            The body of the event posted by the webhook.
	 */
	public void acknowledge(String project, JiraWebHookEvent event) {
		event.eventType().ifPresentOrElse(eventType -> {
			var context = contextPerProject.get(project);
			if (context == null) {
				FailureCollector failureCollector = FailureCollector.collector(reportingConfig);
				failureCollector.critical("Unable to determine handler context for project %s. Was it not configured ?"
						.formatted(project));
				failureCollector.close();
				throw new ConstraintViolationException("Project " + project + " is not configured.", Set.of());
			}

			for (Runnable handler : eventType.handlers(reportingConfig, event, context)) {
				executor.submit(handler);
			}
		}, () -> Log.infof("Event type %s is not supported and cannot be handled.", event.webhookEvent));
	}

	public void syncLastUpdated(String projectGroup) {
		try (FailureCollector failureCollector = FailureCollector.collector(reportingConfig)) {
			Log.infof("Starting scheduled sync of issues for the project group %s", projectGroup);

			JiraConfig.JiraProjectGroup group = jiraConfig.projectGroup().get(projectGroup);
			for (String project : group.projects().keySet()) {
				Log.infof("Generating issues for %s project.", project);
				HandlerProjectContext context = contextPerProject.get(project);

				String query = "project=%s and updated >= %s ORDER BY key".formatted(
						context.project().originalProjectKey(), context.projectGroup().scheduled().timeFilter());
				try {
					syncByQuery(query, context);
				} catch (Exception e) {
					failureCollector
							.warning("Failed to fetch issues for a query '%s': %s".formatted(query, e.getMessage()), e);
					break;
				}
			}
		} finally {
			Log.info("Finished scheduled sync of issues");
		}
	}

	@PreDestroy
	public void finishProcessingAndShutdown() {
		try {
			executor.shutdown();
			if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
				Log.infof("Not all events were processed before the shutdown");
			}
			for (HandlerProjectContext context : contextPerProject.values()) {
				try {
					context.close();
				} catch (Exception e) {
					Log.errorf(e, "Error closing context %s: %s", context, e.getMessage());
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void syncByQuery(String query, HandlerProjectContext context) {
		JiraIssues issues = null;
		int start = 0;
		int max = 100;
		do {
			issues = context.sourceJiraClient().find(query, start, max);
			issues.issues.forEach(jiraIssue -> triggerSyncEvent(jiraIssue, context));

			start += max;
		} while (!issues.issues.isEmpty());
	}

	private void triggerSyncEvent(JiraIssue jiraIssue, HandlerProjectContext context) {
		Log.infof("Adding sync events for a jira issue: %s; Already queued events: %s", jiraIssue.key,
				workQueueSize.get());
		JiraWebHookIssue issue = new JiraWebHookIssue();
		issue.id = jiraIssue.id;
		issue.key = jiraIssue.key;

		JiraWebHookEvent event = new JiraWebHookEvent();
		event.webhookEvent = JiraWebhookEventType.ISSUE_UPDATED.getName();
		event.issue = issue;

		String projectKey = Objects.toString(jiraIssue.fields.project.properties().get("key"));
		acknowledge(projectKey, event);

		// now sync comments:
		if (jiraIssue.fields.comment != null && jiraIssue.fields.comment.comments != null) {
			triggerCommentSyncEvents(projectKey, issue, jiraIssue.fields.comment.comments);
		} else {
			// comments not always come in the jira request... so if we didn't get any, just
			// in case we will query for them:
			JiraComments comments = context.sourceJiraClient().getComments(jiraIssue.id, 0, 500);
			triggerCommentSyncEvents(projectKey, issue, comments.comments);
		}

		// and links:
		if (jiraIssue.fields.issuelinks != null) {
			for (JiraIssueLink link : jiraIssue.fields.issuelinks) {
				event = new JiraWebHookEvent();
				event.webhookEvent = JiraWebhookEventType.ISSUELINK_CREATED.getName();
				event.issueLink = new JiraWebHookIssueLink();
				event.issueLink.id = Long.parseLong(link.id);

				acknowledge(projectKey, event);
			}
		}
	}

	private void triggerCommentSyncEvents(String projectKey, JiraWebHookIssue issue, List<JiraComment> comments) {
		for (JiraComment comment : comments) {
			var event = new JiraWebHookEvent();
			event.comment = new JiraWebHookObject();
			event.comment.id = Long.parseLong(comment.id);
			event.issue = issue;
			event.webhookEvent = JiraWebhookEventType.COMMENT_UPDATED.getName();
			acknowledge(projectKey, event);
		}
	}
}
