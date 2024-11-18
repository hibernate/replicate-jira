package org.hibernate.infra.replicate.jira.service.jira.handler;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraVersion;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraVersionUpsertEventHandler extends JiraEventHandler {

	public JiraVersionUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id) {
		super(reportingConfig, context, id);
	}

	@Override
	protected void doRun() {
		JiraVersion version = context.sourceJiraClient().version(objectId);
		List<JiraVersion> downstreamVersions = context.destinationJiraClient().versions(context.project().projectKey());

		JiraVersion send = new JiraVersion();
		send.name = version.name;
		send.description = prepareVersionDescription(version);
		send.projectId = context.project().projectId();

		Optional<JiraVersion> found = findVersion(version.id, downstreamVersions);
		if (found.isPresent()) {
			context.destinationJiraClient().update(found.get().id, send);
		} else {
			context.destinationJiraClient().create(send);
		}
	}

	private String prepareVersionDescription(JiraVersion version) {
		URI verisonUri = createJiraVersionUri(version);
		String content = """
				{quote}This [version|%s] was created as a copy of %s{quote}


				%s""".formatted(verisonUri, version.name, version.description);
		return truncateContent(content);
	}

	protected Optional<JiraVersion> findVersion(String versionId, List<JiraVersion> versions) {
		if (versions == null || versions.isEmpty()) {
			return Optional.empty();
		}
		for (JiraVersion check : versions) {
			if (hasRequiredVersionQuote(check.description, versionId)) {
				return Optional.of(check);
			}
		}
		return Optional.empty();
	}

	private boolean hasRequiredVersionQuote(String body, String versionId) {
		// e.g. https://hibernate.atlassian.net/projects/HSEARCH/versions/32220/

		return Pattern.compile("(?s)^\\{quote\\}This \\[version.+/versions/%s\\].*".formatted(versionId)).matcher(body)
				.matches();
	}

	@Override
	public String toString() {
		return "JiraVersionUpsertEventHandler{" + "objectId=" + objectId + '}';
	}
}
