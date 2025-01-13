package org.hibernate.infra.replicate.jira.service.jira;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.hibernate.infra.replicate.jira.JiraConfig;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestClient;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraUser;

import io.quarkus.logging.Log;

public final class HandlerProjectGroupContext implements AutoCloseable {

	private final ExecutorService eventHandlingExecutor;
	private final Supplier<Integer> workQueueSize;

	private final String projectGroupName;
	private final JiraRestClient sourceJiraClient;
	private final JiraRestClient destinationJiraClient;

	private final ExecutorService downstreamEventHandlingExecutor;
	private final Supplier<Integer> downstreamWorkQueueSize;
	private final ScheduledExecutorService rateLimiterExecutor = Executors.newScheduledThreadPool(1);
	private final Semaphore rateLimiter;
	private final Semaphore downstreamRateLimiter;
	private final JiraConfig.JiraProjectGroup projectGroup;
	private final Map<String, String> invertedUsers;
	private final Map<String, String> invertedStatuses;
	private final Map<String, String> invertedResolutions;
	private final Map<String, HandlerProjectContext> projectContexts;
	private final Pattern sourceLabelPattern;
	private final JiraUser notMappedAssignee;
	private final DateTimeFormatter formatter;

	public HandlerProjectGroupContext(String projectGroupName, JiraConfig.JiraProjectGroup projectGroup,
			JiraRestClient source, JiraRestClient destination) {
		this.projectGroupName = projectGroupName;
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
		this.invertedResolutions = invert(projectGroup.resolutions().mapping());
		this.sourceJiraClient = source;
		this.destinationJiraClient = destination;

		Map<String, HandlerProjectContext> projectContexts = new HashMap<>();

		for (var project : projectGroup.projects().entrySet()) {
			projectContexts.put(project.getKey(), new HandlerProjectContext(project.getKey(), this));
		}
		this.projectContexts = Collections.unmodifiableMap(projectContexts);

		this.notMappedAssignee = projectGroup().users().notMappedAssignee()
				.map(v -> new JiraUser(projectGroup().users().mappedPropertyName(), v)).orElse(null);

		this.sourceLabelPattern = Pattern.compile(projectGroup().formatting().labelTemplate().formatted(".+"));
		this.formatter = DateTimeFormatter.ofPattern(projectGroup().formatting().timestampFormat());
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

	public String projectGroupName() {
		return projectGroupName;
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

	public JiraRestClient sourceJiraClient() {
		return sourceJiraClient;
	}

	public JiraRestClient destinationJiraClient() {
		return destinationJiraClient;
	}

	public HandlerProjectContext contextForProject(String project) {
		HandlerProjectContext context = projectContexts.get(project);
		if (context == null) {
			throw new IllegalStateException("No handler context for project %s".formatted(project));
		}
		return context;
	}

	public Optional<HandlerProjectContext> findContextForProject(String project) {
		if (!projectGroup().projects().containsKey(project)) {
			// different project group, don't bother
			return Optional.empty();
		}
		return Optional.ofNullable(projectContexts.get(project));
	}

	public Optional<HandlerProjectContext> findContextForOriginalProjectKey(String project) {

		for (HandlerProjectContext context : projectContexts.values()) {
			if (context.project().originalProjectKey().equals(project)) {
				return Optional.of(context);
			}
		}

		return Optional.empty();
	}

	public HandlerProjectContext contextForOriginalProjectKey(String project) {

		for (HandlerProjectContext context : projectContexts.values()) {
			if (context.project().originalProjectKey().equals(project)) {
				return context;
			}
		}

		throw new IllegalStateException("No handler context for project %s".formatted(project));
	}

	@Override
	public void close() {
		projectContexts.values().forEach(HandlerProjectContext::close);
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

	public String upstreamResolution(String mappedValue) {
		return invertedResolutions.get(mappedValue);
	}

	public boolean isUserIgnored(String triggeredByUser) {
		return projectGroup().users().ignoredUpstreamUsers().contains(triggeredByUser);
	}

	public boolean isDownstreamUserIgnored(String triggeredByUser) {
		return projectGroup().users().ignoredDownstreamUsers().contains(triggeredByUser);
	}

	public JiraUser notMappedAssignee() {
		return notMappedAssignee;
	}

	public boolean isSourceLabel(String label) {
		return sourceLabelPattern.matcher(label).matches();
	}

	public String formatTimestamp(ZonedDateTime time) {
		return time != null ? time.format(formatter) : "";
	}

}
