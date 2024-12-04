package org.hibernate.infra.replicate.jira.service.jira;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

	private final ExecutorService downstreamEventHandlingExecutor;
	private final Supplier<Integer> downstreamWorkQueueSize;
	private final ScheduledExecutorService rateLimiterExecutor = Executors.newScheduledThreadPool(1);
	private final Semaphore rateLimiter;
	private final Semaphore downstreamRateLimiter;
	private final JiraConfig.JiraProjectGroup projectGroup;
	private final Map<String, String> invertedUsers;
	private final Map<String, String> invertedStatuses;

	public HandlerProjectGroupContext(JiraConfig.JiraProjectGroup projectGroup) {
		this.projectGroup = projectGroup;

		JiraConfig.EventProcessing processing = projectGroup.processing();

		final int permits = processing.eventsPerTimeframe();
		this.rateLimiter = new Semaphore(permits);
		this.downstreamRateLimiter = new Semaphore(permits);
		rateLimiterExecutor.scheduleAtFixedRate(() -> {
			rateLimiter.drainPermits();
			rateLimiter.release(permits);
			downstreamRateLimiter.drainPermits();
			downstreamRateLimiter.release(permits);
		}, processing.timeframeInSeconds(), processing.timeframeInSeconds(), TimeUnit.SECONDS);

		LinkedBlockingDeque<Runnable> workQueue = new LinkedBlockingDeque<>(processing.queueSize());
		workQueueSize = workQueue::size;
		eventHandlingExecutor = new ThreadPoolExecutor(processing.threads(), processing.threads(), 0L,
				TimeUnit.MILLISECONDS, workQueue);

		LinkedBlockingDeque<Runnable> downstreamWorkQueue = new LinkedBlockingDeque<>(processing.queueSize());
		downstreamWorkQueueSize = downstreamWorkQueue::size;
		downstreamEventHandlingExecutor = new ThreadPoolExecutor(processing.threads(), processing.threads(), 0L,
				TimeUnit.MILLISECONDS, downstreamWorkQueue);

		this.invertedUsers = invert(projectGroup.users().mapping());
		this.invertedStatuses = invert(projectGroup.statuses().mapping());
	}

	private static Map<String, String> invert(Map<String, String> map) {
		Map<String, String> result = new HashMap<>();
		for (var entry : map.entrySet()) {
			result.put(entry.getValue(), entry.getKey());
		}
		return Collections.unmodifiableMap(result);
	}

	public void startProcessingEvent() throws InterruptedException {
		rateLimiter.acquire(1);
	}

	public void startProcessingDownstreamEvent() throws InterruptedException {
		downstreamRateLimiter.acquire(1);
	}

	public JiraConfig.JiraProjectGroup projectGroup() {
		return projectGroup;
	}

	public int pendingEventsInCurrentContext() {
		return workQueueSize.get();
	}

	public int pendingDownstreamEventsInCurrentContext() {
		return downstreamWorkQueueSize.get();
	}

	public void submitTask(Runnable task) {
		eventHandlingExecutor.submit(task);
	}

	public void submitDownstreamTask(Runnable task) {
		downstreamEventHandlingExecutor.submit(task);
	}

	@Override
	public void close() {
		// when requesting to close the context we aren't expecting to process any other
		// events hence there's no point in continuing "releasing" more "permits":
		if (!rateLimiterExecutor.isShutdown()) {
			rateLimiterExecutor.shutdownNow();
		}
		closeEventExecutor(eventHandlingExecutor);
		closeEventExecutor(downstreamEventHandlingExecutor);
	}

	private static void closeEventExecutor(ExecutorService executor) {
		if (!executor.isShutdown()) {
			try {
				executor.shutdown();
				if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
					Log.warnf("Not all events were processed before the shutdown");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public String upstreamUser(String mappedValue) {
		return invertedUsers.get(mappedValue);
	}

	public String upstreamStatus(String mappedValue) {
		return invertedStatuses.get(mappedValue);
	}

}
