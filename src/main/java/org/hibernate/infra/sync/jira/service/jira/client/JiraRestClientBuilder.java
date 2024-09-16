package org.hibernate.infra.sync.jira.service.jira.client;

import java.util.Base64;

import org.hibernate.infra.sync.jira.JiraConfig;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class JiraRestClientBuilder {

	public static JiraRestClient of(JiraConfig.Instance jira) {
		JiraConfig.JiraUser jiraUser = jira.apiUser();

		return QuarkusRestClientBuilder.newBuilder()
				.baseUri( jira.apiUri() )
				// TODO: Add a custom client logger that cleans up auth headers so that we do not write any secrets to the logs ...
				.clientHeadersFactory( (incomingHeaders, clientOutgoingHeaders) -> {
					clientOutgoingHeaders.add( "Authorization", authorizationHeaderValue( jiraUser.email(), jiraUser.token() ) );
					return clientOutgoingHeaders;
				} )
				.build( JiraRestClient.class );
	}

	private static String authorizationHeaderValue(String username, String password) {
		return "Basic " + Base64.getEncoder().encodeToString( ( username + ":" + password ).getBytes() );
	}
}
