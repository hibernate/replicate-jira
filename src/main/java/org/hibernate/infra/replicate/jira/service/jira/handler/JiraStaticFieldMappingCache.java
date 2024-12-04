package org.hibernate.infra.replicate.jira.service.jira.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class JiraStaticFieldMappingCache {

	private static final Map<String, Map<String, String>> priorities = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> issueType = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> status = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> statusUpstream = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> linkType = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> user = new ConcurrentHashMap<>();

	public static String priority(String projectKey, String sourceId, Function<String, Map<String, String>> onMissing,
			String defaultValue) {
		return priorities.computeIfAbsent(projectKey, onMissing).getOrDefault(sourceId, defaultValue);
	}

	public static String issueType(String projectGroup, String sourceId,
			Function<String, Map<String, String>> onMissing, String defaultValue) {
		return issueType.computeIfAbsent(projectGroup, onMissing).getOrDefault(sourceId, defaultValue);
	}

	public static String status(String projectGroup, String transitionKey, Function<String, String> onMissing) {
		return status(status, projectGroup, transitionKey, onMissing);
	}

	public static String statusUpstream(String projectGroup, String transitionKey, Function<String, String> onMissing) {
		return status(statusUpstream, projectGroup, transitionKey, onMissing);
	}

	private static String status(Map<String, Map<String, String>> status, String projectGroup, String transitionKey,
			Function<String, String> onMissing) {
		Map<String, String> groupStatuses = status.computeIfAbsent(projectGroup, pg -> new ConcurrentHashMap<>());

		String id = groupStatuses.get(transitionKey);
		if (id == null) {
			id = onMissing.apply(transitionKey);
			if (id != null) {
				groupStatuses.put(transitionKey, id);
			}
		}

		return id;
	}

	public static String linkType(String projectGroup, String sourceId, Function<String, Map<String, String>> onMissing,
			String defaultValue) {
		return linkType.computeIfAbsent(projectGroup, onMissing).getOrDefault(sourceId, defaultValue);
	}

	public static String user(String projectGroup, String sourceId, Function<String, String> onMissing) {
		return user.computeIfAbsent(projectGroup, pk -> new HashMap<>()).computeIfAbsent(sourceId, onMissing);
	}

}
