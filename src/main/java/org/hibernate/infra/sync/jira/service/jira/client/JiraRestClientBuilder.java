package org.hibernate.infra.sync.jira.service.jira.client;

import java.util.Map;

import org.hibernate.infra.sync.jira.JiraConfig;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class JiraRestClientBuilder {

	public static JiraRestClient of(JiraConfig.Instance jira) {
		JiraConfig.JiraUser jiraUser = jira.apiUser();

		Map<String, String> headers = jira.loginKind().headers( jiraUser.email(), jiraUser.token() );

		return QuarkusRestClientBuilder.newBuilder()
				.baseUri( jira.apiUri() )
				// TODO: Add a custom client logger that cleans up auth headers so that we do not write any secrets to the logs ...
				.clientHeadersFactory( (incomingHeaders, clientOutgoingHeaders) -> {
					for ( var entry : headers.entrySet() ) {
						clientOutgoingHeaders.add( entry.getKey(), entry.getValue() );
					}
					return clientOutgoingHeaders;
				} )
				.build( JiraRestClient.class );
	}
}
