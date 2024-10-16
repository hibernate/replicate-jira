package org.hibernate.infra.replicate.jira.service.jira;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.hibernate.infra.replicate.jira.JiraConfig;

public final class HandlerProjectGroupContext implements AutoCloseable {

	private final ScheduledExecutorService rateLimiterExecutor = Executors.newScheduledThreadPool(1);
	private final Semaphore rateLimiter;
	private final JiraConfig.JiraProjectGroup projectGroup;

	public HandlerProjectGroupContext(JiraConfig.JiraProjectGroup projectGroup) {
		this.projectGroup = projectGroup;

		final int permits = projectGroup.processing().eventsPerTimeframe();
		this.rateLimiter = new Semaphore(permits);
		rateLimiterExecutor.scheduleAtFixedRate(() -> {
			rateLimiter.drainPermits();
			rateLimiter.release(permits);
		}, projectGroup.processing().timeframeInSeconds(), projectGroup.processing().timeframeInSeconds(),
				TimeUnit.SECONDS);
	}

	public void startProcessingEvent() throws InterruptedException {
		rateLimiter.acquire(1);
	}

	public JiraConfig.JiraProjectGroup projectGroup() {
		return projectGroup;
	}

	@Override
	public void close() {
		// when requesting to close the context we aren't expecting to process any other
		// events hence there's no point in continuing "releasing" more "permits":
		if (!rateLimiterExecutor.isShutdown()) {
			rateLimiterExecutor.shutdownNow();
		}
	}
}
