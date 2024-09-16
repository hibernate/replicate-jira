package org.hibernate.infra.sync.jira.service.jira;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.hibernate.infra.sync.jira.JiraConfig;
import org.hibernate.infra.sync.jira.ProcessingConfig;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestClient;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestClientBuilder;
import org.hibernate.infra.sync.jira.service.jira.model.hook.JiraWebHookEvent;
import org.hibernate.infra.sync.jira.service.reporting.FailureCollector;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JiraService {

	private final ReportingConfig reportingConfig;
	private final ExecutorService executor;
	private final Map<String, HandlerProjectContext> contextPerProject;

	@Inject
	public JiraService(ProcessingConfig processingConfig, JiraConfig jiraConfig, ReportingConfig reportingConfig) {
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

	private record JiraRestClientPair(JiraRestClient source, JiraRestClient destination) {
	}
}
