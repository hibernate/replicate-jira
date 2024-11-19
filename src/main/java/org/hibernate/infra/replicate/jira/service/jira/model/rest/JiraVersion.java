package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.hibernate.infra.replicate.jira.JiraConfig;
import org.hibernate.infra.replicate.jira.service.jira.model.JiraBaseObject;

import jakarta.ws.rs.core.UriBuilder;

public class JiraVersion extends JiraBaseObject {

	public URI self;
	public String id;
	public String name;
	public String description;
	public String projectId;
	public String releaseDate;
	public boolean released;

	public JiraVersion() {
	}

	public JiraVersion(String id) {
		this.id = id;
	}

	public JiraVersion copyForProject(JiraConfig.JiraProject project) {
		JiraVersion copy = new JiraVersion();
		copy.name = name;
		copy.description = prepareVersionDescriptionForCopy();
		copy.projectId = project.projectId();
		copy.releaseDate = releaseDate;
		copy.released = released;
		return copy;
	}

	public String prepareVersionDescriptionForCopy() {
		URI verisonUri = createJiraVersionUri(this);
		return """
				{quote}This [version|%s] was created as a copy of %s{quote}


				%s""".formatted(verisonUri, this.name, Objects.toString(this.description, "")).trim();
	}

	public static Optional<JiraVersion> findVersion(String versionId, List<JiraVersion> versions) {
		if (versions == null || versions.isEmpty()) {
			return Optional.empty();
		}
		// e.g. https://hibernate.atlassian.net/projects/HSEARCH/versions/32220/
		Pattern pattern = Pattern.compile("(?s)^\\{quote\\}This \\[version.+/versions/%s\\].*".formatted(versionId));
		for (JiraVersion check : versions) {
			if (pattern.matcher(check.description).matches()) {
				return Optional.of(check);
			}
		}
		return Optional.empty();
	}

	private static URI createJiraVersionUri(JiraVersion version) {
		// e.g.
		// https://hibernate.atlassian.net/projects/HSEARCH/versions/32220
		return UriBuilder.fromUri(version.self).replacePath("projects").path(version.projectId).path("versions")
				.path(version.id).replaceQuery("").build();
	}

}
