package org.hibernate.infra.sync.jira.service.jira;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssueLink;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssues;
import org.hibernate.infra.sync.jira.service.reporting.FailureCollector;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JiraService {

	private final ReportingConfig reportingConfig;
	private final ExecutorService executor;
	private final Map<String, HandlerProjectContext> contextPerProject;
	private final JiraConfig jiraConfig;

	@Inject
	public JiraService(ProcessingConfig processingConfig, JiraConfig jiraConfig, ReportingConfig reportingConfig, Scheduler scheduler) {
		executor = new ThreadPoolExecutor(
				processingConfig.threads(), processingConfig.threads(),
				0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingDeque<>( processingConfig.queueSize() )
		);

		Map<String, HandlerProjectContext> contextMap = new HashMap<>();
		for ( var entry : jiraConfig.projectGroup().entrySet() ) {
			JiraRestClient source = JiraRestClientBuilder.of( entry.getValue().source() );
			JiraRestClient destination = JiraRestClientBuilder.of( entry.getValue().destination() );

			for ( var project : entry.getValue().projects().entrySet() ) {
				contextMap.put( project.getKey(), new HandlerProjectContext(
						project.getKey(),
						entry.getKey(),
						source, destination,
						entry.getValue()
				) );
			}
		}

		this.contextPerProject = Collections.unmodifiableMap( contextMap );
		this.reportingConfig = reportingConfig;

		configureScheduledTasks( scheduler, jiraConfig );
		this.jiraConfig = jiraConfig;
	}

	private void configureScheduledTasks(Scheduler scheduler, JiraConfig jiraConfig) {
		for ( var entry : jiraConfig.projectGroup().entrySet() ) {
			scheduler.newJob( "Sync project group %s".formatted( entry.getKey() ) )
					.setCron( entry.getValue().scheduled().cron() )
					.setConcurrentExecution( Scheduled.ConcurrentExecution.SKIP )
					.setTask( executionContext -> {
						syncLastUpdated( entry.getKey() );
					} )
					.schedule();
		}
	}

	/**
	 * At this point we want to only make some simple validation of the request body,
	 * and then add a request to the processing queue.
	 * We do not want to start doing actual processing before replying back to the Jira that we've received the event.
	 * It may be that processing will take some time, and it may result in a timeout on the hook side.
	 *
	 * @param project The project key as defined in the {@link org.hibernate.infra.sync.jira.JiraConfig.JiraProjectGroup#projects()}
	 * @param event The body of the event posted by the webhook.
	 */
	public void acknowledge(String project, JiraWebHookEvent event) {
		event.eventType().ifPresentOrElse( eventType -> {
			var context = contextPerProject.get( project );
			if ( context == null ) {
				FailureCollector failureCollector = FailureCollector.collector( reportingConfig );
				failureCollector.critical( "Unable to determine handler context for project %s. Was it not configured ?".formatted( project ) );
				failureCollector.close();
				return;
			}

			for ( Runnable handler : eventType.handlers( reportingConfig, event, context ) ) {
				executor.submit( handler );
			}
		}, () -> Log.infof( "Event type %s is not supported and cannot be handled.", event.webhookEvent ) );
	}

	public void syncLastUpdated(String projectGroup) {
		try ( FailureCollector failureCollector = FailureCollector.collector( reportingConfig ) ) {
			Log.infof( "Starting scheduled sync of issues for the project group %s", projectGroup );

			JiraConfig.JiraProjectGroup group = jiraConfig.projectGroup().get( projectGroup );
			for ( String project : group.projects().keySet() ) {
				Log.infof( "Generating issues for %s project.", project );
				HandlerProjectContext context = contextPerProject.get( project );

				String query = "project=%s and updated >= %s ORDER BY key".formatted(
						context.project().originalProjectKey(),
						context.projectGroup().scheduled().timeFilter()
				);

				JiraIssues issues = null;
				int start = 0;
				int max = 100;
				do {
					try {
						issues = context.sourceJiraClient().find( query, start, max );
						issues.issues.forEach( this::triggerSyncEvent );
					}
					catch (Exception e) {
						failureCollector.warning( "Failed to fetch issues for a query '%s': %s".formatted( query, e.getMessage() ), e );
						break;
					}

					start += max;
				} while ( !issues.issues.isEmpty() );
			}

		}
		finally {
			Log.info( "Finished scheduled sync of issues" );
		}
	}

	private void triggerSyncEvent(JiraIssue jiraIssue) {
		JiraWebHookEvent event = new JiraWebHookEvent();
		event.webhookEvent = JiraWebhookEventType.ISSUE_UPDATED.getName();
		event.issue = new JiraWebHookIssue();
		event.issue.id = jiraIssue.id;
		event.issue.key = jiraIssue.key;

		String projectKey = Objects.toString( jiraIssue.fields.project.properties().get( "key" ) );
		acknowledge( projectKey, event );

		// now sync comments:
		if ( jiraIssue.fields.comment != null && jiraIssue.fields.comment.comments != null ) {
			for ( JiraComment comment : jiraIssue.fields.comment.comments ) {
				event = new JiraWebHookEvent();
				event.comment = new JiraWebHookObject();
				event.comment.id = Long.parseLong( comment.id );
				event.webhookEvent = JiraWebhookEventType.COMMENT_UPDATED.getName();
				acknowledge( projectKey, event );
			}
		}

		// and links:
		if ( jiraIssue.fields.issuelinks != null ) {
			for ( JiraIssueLink link : jiraIssue.fields.issuelinks ) {
				event = new JiraWebHookEvent();
				event.webhookEvent = JiraWebhookEventType.ISSUELINK_CREATED.getName();
				event.issueLink = new JiraWebHookIssueLink();
				event.issueLink.id = Long.parseLong( link.id );

				acknowledge( projectKey, event );
			}
		}

	}
}
