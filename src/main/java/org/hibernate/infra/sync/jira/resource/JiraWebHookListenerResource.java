package org.hibernate.infra.sync.jira.resource;

import org.hibernate.infra.sync.jira.service.jira.JiraService;
import org.hibernate.infra.sync.jira.service.jira.model.hook.JiraWebHookEvent;

import org.jboss.resteasy.reactive.RestPath;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@Path("/jira/webhooks")
public class JiraWebHookListenerResource {

	@Inject
	JiraService jiraService;

	@POST
	@Path("/{project}")
	@Consumes(MediaType.APPLICATION_JSON)
	public String somethingHappened(@RestPath @NotNull /* @ConfiguredProject */ String project,
			JiraWebHookEvent event) {
		Log.debugf("Received a notification about %s project: %s", project, event);
		jiraService.acknowledge(project, event);
		return "ack";
	}
}
