package org.hibernate.infra.replicate.jira.resource;

import org.hibernate.infra.replicate.jira.service.jira.JiraService;
import org.hibernate.infra.replicate.jira.service.jira.model.hook.JiraWebHookEvent;
import org.hibernate.infra.replicate.jira.service.validation.ConfiguredProject;

import org.jboss.resteasy.reactive.RestPath;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/jira/webhooks")
public class JiraWebHookListenerResource {

	@Inject
	JiraService jiraService;

	@POST
	@Path("/{project}")
	@Consumes(MediaType.APPLICATION_JSON)
	public String somethingHappenedUpstream(@RestPath @NotNull @ConfiguredProject String project,
			@QueryParam("triggeredByUser") String triggeredByUser, JiraWebHookEvent event) {
		Log.infof("Received a notification about %s project: %.200s...", project, event);
		jiraService.acknowledge(project, event, triggeredByUser);
		return "ack";
	}
}
