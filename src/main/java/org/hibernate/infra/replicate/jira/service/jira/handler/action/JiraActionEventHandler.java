package org.hibernate.infra.replicate.jira.service.jira.handler.action;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.reporting.FailureCollector;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

import io.quarkus.logging.Log;

public abstract class JiraActionEventHandler implements Runnable {

	protected final JiraActionEvent event;
	protected final FailureCollector failureCollector;
	protected final HandlerProjectGroupContext context;

	protected JiraActionEventHandler(ReportingConfig reportingConfig, HandlerProjectGroupContext context,
			JiraActionEvent event) {
		this.event = event;
		this.failureCollector = FailureCollector.collector(reportingConfig);
		this.context = context;
	}

	@Override
	public final void run() {
		try {
			context.startProcessingDownstreamEvent();
			doRun();
		} catch (RuntimeException e) {
			failureCollector.critical("Failed to handle the event: %s".formatted(this), e);
		} catch (InterruptedException e) {
			failureCollector.critical("Interrupted while waiting in the queue", e);
			Thread.currentThread().interrupt();
		} finally {
			failureCollector.close();
			Log.infof("Finished processing %s. Pending events in %s to process: %s", this.toString(),
					context.projectGroupName(), context.pendingDownstreamEventsInCurrentContext());
		}
	}

	protected abstract void doRun();

	public abstract String toString();

}
