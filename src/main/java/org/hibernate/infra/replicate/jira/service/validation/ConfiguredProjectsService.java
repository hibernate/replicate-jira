package org.hibernate.infra.replicate.jira.service.validation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.infra.replicate.jira.JiraConfig;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfiguredProjectsService {

	private final Set<String> upstreamProjects;
	private final Set<String> downstreamProjects;
	private final Set<String> projectGroups;

	public ConfiguredProjectsService(JiraConfig jiraConfig) {
		Set<String> down = new HashSet<>();
		Set<String> up = new HashSet<>();
		Set<String> groups = new HashSet<>();
		for (var group : jiraConfig.projectGroup().entrySet()) {
			groups.add(group.getKey());
			for (JiraConfig.JiraProject project : group.getValue().projects().values()) {
				up.add(project.originalProjectKey());
				down.add(project.projectKey());
			}
		}

		upstreamProjects = Collections.unmodifiableSet(up);
		downstreamProjects = Collections.unmodifiableSet(down);
		projectGroups = Collections.unmodifiableSet(groups);
	}

	public boolean isUpstreamProject(String projectName) {
		return upstreamProjects.contains(projectName);
	}

	public boolean isDownstreamProject(String projectName) {
		return downstreamProjects.contains(projectName);
	}

	public boolean isProjectGroup(String value) {
		return projectGroups.contains(value);
	}
}
