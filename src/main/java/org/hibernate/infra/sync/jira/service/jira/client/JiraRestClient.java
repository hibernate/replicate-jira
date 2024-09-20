package org.hibernate.infra.sync.jira.service.jira.client;


import java.util.List;

import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComment;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComments;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssueLink;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssueLinkTypes;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssueResponse;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssueTypes;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssues;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraRemoteLink;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraSimpleObject;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraUser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The REST client to make various JIRA operations that we are interested in.
 * Note: the client assumes that the base URI for it is defined up to the API version (included), e.g.:
 */
public interface JiraRestClient {

	@GET
	@Path("/issue/{key}")
	JiraIssue getIssue(@PathParam("key") String key);

	@GET
	@Path("/issue/{id}")
	JiraIssue getIssue(@PathParam("id") Long id);

	@POST
	@Path("/issue")
	@Consumes(MediaType.APPLICATION_JSON)
	JiraIssueResponse create(JiraIssue issue);

	@PUT
	@Path("/issue/{key}")
	@Consumes(MediaType.APPLICATION_JSON)
	JiraIssueResponse update(@PathParam("key") String key, JiraIssue issue);

	@POST
	@Path("/issue/{key}/remotelink")
	@Consumes(MediaType.APPLICATION_JSON)
	void upsertRemoteLink(@PathParam("key") String key, JiraRemoteLink remoteLink);

	@GET
	@Path("/issue/{issueId}/comment/{commentId}")
	JiraComment getComment(@PathParam("issueId") Long issueId, @PathParam("commentId") Long commentId);

	@GET
	@Path("/issue/{issueKey}/comment/{commentId}")
	JiraComment getComment(@PathParam("issueKey") String issueKey, @PathParam("commentId") Long commentId);

	@GET
	@Path("/issue/{issueId}/comment")
	JiraComments getComments(@PathParam("issueId") Long issueId, @QueryParam("startAt") int startAt, @QueryParam("maxResults") int maxResults);

	@GET
	@Path("/issue/{issueKey}/comment")
	JiraComments getComments(@PathParam("issueKey") String issueKey, @QueryParam("startAt") int startAt, @QueryParam("maxResults") int maxResults);

	@POST
	@Path("/issue/{issueKey}/comment")
	@Consumes(MediaType.APPLICATION_JSON)
	JiraIssueResponse create(@PathParam("issueKey") String issueKey, JiraComment comment);

	@PUT
	@Path("/issue/{issueKey}/comment/{commentId}")
	@Consumes(MediaType.APPLICATION_JSON)
	JiraIssueResponse update(@PathParam("issueKey") String issueKey, @PathParam("commentId") String commentId, JiraComment comment);

	@GET
	@Path("/priority")
	List<JiraSimpleObject> getPriorities();

	@GET
	@Path("/issuetype")
	List<JiraSimpleObject> getIssueTypes();

	@GET
	@Path("/statuses/search")
	JiraIssueTypes getStatues();

	@GET
	@Path("/issueLinkType")
	JiraIssueLinkTypes getIssueLinkTypes();

	@GET
	@Path("/user/search")
	List<JiraUser> findUser(@QueryParam("query") String email);

	@GET
	@Path("/issueLink/{id}")
	JiraIssueLink getIssueLink(@PathParam("id") Long id);

	@POST
	@Path("/issueLink")
	void upsertIssueLink(JiraIssueLink link);

	@DELETE
	@Path("/issue/{issueKey}/comment/{commentId}")
	void deleteComment(@PathParam("issueKey") String issueKey, @PathParam("commentId") String commentId);

	@DELETE
	@Path("/issueLink/{linkId}")
	void deleteIssueLink(@PathParam("linkId") String linkId);

	@GET
	@Path( "/search" )
	JiraIssues find(@QueryParam("jql") String query, @QueryParam("startAt") int startAt, @QueryParam("maxResults") int maxResults);

	@ClientObjectMapper
	static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
		return defaultObjectMapper.copy()
				.setDefaultPropertyInclusion( JsonInclude.Include.NON_EMPTY );
	}

	@ClientExceptionMapper
	static RuntimeException toException(Response response) {
		if ( response.getStatusInfo().getFamily() == Response.Status.Family.CLIENT_ERROR
				|| response.getStatusInfo().getFamily() == Response.Status.Family.SERVER_ERROR ) {
			return new JiraRestException( "Encountered an error calling Jira REST API. See server logs for details", response.getStatus() );
		}
		return null;
	}
}
