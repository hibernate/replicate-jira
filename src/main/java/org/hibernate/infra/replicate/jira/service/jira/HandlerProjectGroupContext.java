package org.hibernate.infra.replicate.jira.service.jira;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.hibernate.infra.replicate.jira.JiraConfig;

import io.quarkus.logging.Log;

public final class HandlerProjectGroupContext implements AutoCloseable {

	private final ExecutorService eventHandlingExecutor;
	private final Supplier<Integer> workQueueSize;
	private final ScheduledExecutorService rateLimiterExecutor = Executors.newScheduledThreadPool(1);
	private final Semaphore rateLimiter;
	private final JiraConfig.JiraProjectGroup projectGroup;

	public HandlerProjectGroupContext(JiraConfig.JiraProjectGroup projectGroup) {
		this.projectGroup = projectGroup;

		JiraConfig.EventProcessing processing = projectGroup.processing();

		final int permits = processing.eventsPerTimeframe();
		this.rateLimiter = new Semaphore(permits);
		rateLimiterExecutor.scheduleAtFixedRate(() -> {
			rateLimiter.drainPermits();
			rateLimiter.release(permits);
		}, processing.timeframeInSeconds(), processing.timeframeInSeconds(), TimeUnit.SECONDS);

		LinkedBlockingDeque<Runnable> workQueue = new LinkedBlockingDeque<>(processing.queueSize());
		workQueueSize = workQueue::size;
		eventHandlingExecutor = new ThreadPoolExecutor(processing.threads(), processing.threads(), 0L,
				TimeUnit.MILLISECONDS, workQueue);
	}

	public void startProcessingEvent() throws InterruptedException {
		rateLimiter.acquire(1);
	}

	public JiraConfig.JiraProjectGroup projectGroup() {
		return projectGroup;
	}

	public int pendingEventsInCurrentContext() {
		return workQueueSize.get();
	}

	public void submitTask(Runnable task) {
		eventHandlingExecutor.submit(task);
	}

	@Override
	public void close() {
		// when requesting to close the context we aren't expecting to process any other
		// events hence there's no point in continuing "releasing" more "permits":
		if (!rateLimiterExecutor.isShutdown()) {
			rateLimiterExecutor.shutdownNow();
		}
		if (!eventHandlingExecutor.isShutdown()) {
			try {
				eventHandlingExecutor.shutdown();
				if (!eventHandlingExecutor.awaitTermination(2, TimeUnit.MINUTES)) {
					Log.warnf("Not all events were processed before the shutdown");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
