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

	public ConfiguredProjectsService(JiraConfig jiraConfig) {
		Set<String> down = new HashSet<>();
		Set<String> up = new HashSet<>();
		for (JiraConfig.JiraProjectGroup group : jiraConfig.projectGroup().values()) {
			for (JiraConfig.JiraProject project : group.projects().values()) {
				up.add(project.originalProjectKey());
				down.add(project.projectKey());
			}
		}

		upstreamProjects = Collections.unmodifiableSet(up);
		downstreamProjects = Collections.unmodifiableSet(down);
	}

	public boolean isUpstreamProject(String projectName) {
		return upstreamProjects.contains(projectName);
	}

	public boolean isDownstreamProject(String projectName) {
		return downstreamProjects.contains(projectName);
	}
}
