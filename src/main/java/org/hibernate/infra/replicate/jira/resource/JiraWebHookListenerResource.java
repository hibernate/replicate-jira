package org.hibernate.infra.replicate.jira.resource;

import org.hibernate.infra.replicate.jira.service.jira.JiraService;
import org.hibernate.infra.replicate.jira.service.jira.model.action.JiraActionEvent;
import org.hibernate.infra.replicate.jira.service.jira.model.hook.JiraWebHookEvent;
import org.hibernate.infra.replicate.jira.service.validation.ConfiguredProject;
import org.hibernate.infra.replicate.jira.service.validation.ConfiguredProjectGroup;

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
	@Path("/source/{projectGroup}")
	@Consumes(MediaType.APPLICATION_JSON)
	public String somethingHappenedUpstream(@RestPath @NotNull @ConfiguredProjectGroup String projectGroup,
			@QueryParam("triggeredByUser") String triggeredByUser, JiraWebHookEvent event) {
		Log.tracef("Received a notification about %s project group: %.200s...", projectGroup, event);
		jiraService.acknowledge(projectGroup, event, triggeredByUser);
		return "ack";
	}

	@POST
	@Path("/mirror/{projectGroup}/{project}")
	@Consumes(MediaType.APPLICATION_JSON)
	public String somethingHappenedDownstream(@RestPath @NotNull @ConfiguredProjectGroup String projectGroup,
			@RestPath @NotNull @ConfiguredProject String project, JiraActionEvent data) {
		Log.tracef("Received a downstream notification about %s project: %s...", project, data);
		jiraService.downstreamAcknowledge(projectGroup, data);
		return "ack";
	}
}
