package org.hibernate.infra.sync.jira.service.jira.handler;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.hibernate.infra.sync.jira.JiraConfig;
import org.hibernate.infra.sync.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComment;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssueResponse;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraSimpleObject;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraUser;
import org.hibernate.infra.sync.jira.service.reporting.FailureCollector;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

import jakarta.ws.rs.core.UriBuilder;

public abstract class JiraEventHandler implements Runnable {
	private static final int MAX_ATTEMPTS = 20;

	protected final Long objectId;
	protected final FailureCollector failureCollector;
	protected final HandlerProjectContext context;
	private final Pattern keyToUpdatePattern;

	protected JiraEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id) {
		this.objectId = id;
		this.failureCollector = FailureCollector.collector( reportingConfig );
		this.context = context;
		this.keyToUpdatePattern = Pattern.compile( "^%s-\\d+".formatted( context.project().originalProjectKey() ) );
	}

	protected static URI createJiraIssueUri(JiraIssue sourceIssue) {
		// e.g. https://hibernate.atlassian.net/browse/JIRATEST1-1
		return UriBuilder.fromUri( sourceIssue.self )
				.replacePath( "browse" )
				.path( sourceIssue.key )
				.build();
	}

	protected static URI createJiraCommentUri(JiraIssue issue, JiraComment comment) {
		// e.g. https://hibernate.atlassian.net/browse/JIRATEST1-1?focusedCommentId=116651
		return UriBuilder.fromUri( issue.self )
				.replacePath( "browse" )
				.path( issue.key )
				.replaceQuery( "" )
				.queryParam( "focusedCommentId", comment.id )
				.build();
	}

	protected static URI createJiraUserUri(URI someJiraUri, JiraUser user) {
		// e.g. https://hibernate.atlassian.net/jira/people/557058:18cf44bb-bc9b-4e8d-b1b7-882969ddc8e5
		if ( user == null ) {
			return null;
		}
		return UriBuilder.fromUri( someJiraUri )
				.replacePath( "jira" )
				.path( "people" )
				.path( user.accountId )
				.build();
	}

	protected Optional<String> priority(String sourceId) {
		JiraConfig.ValueMapping mappedValues = context.projectGroup().priorities();
		return Optional.ofNullable( JiraStaticFieldMappingCache.priority( context.projectGroupName(), sourceId, pk -> {
			Map<String, String> mapping = mappedValues.mapping();
			if ( !mapping.isEmpty() ) {
				return mapping;
			}

			// Otherwise we'll try to use REST to get the info and match, but that may not necessarily work fine
			List<JiraSimpleObject> source = context.sourceJiraClient().getPriorities();
			List<JiraSimpleObject> destination = context.destinationJiraClient().getPriorities();

			return createMapping( source, destination );
		}, mappedValues.defaultValue() ) );
	}

	protected Optional<String> issueType(String sourceId) {
		JiraConfig.ValueMapping mappedValues = context.projectGroup().issueTypes();
		return Optional.ofNullable( JiraStaticFieldMappingCache.issueType( context.projectGroupName(), sourceId, pk -> {

			Map<String, String> mapping = context.projectGroup().issueTypes().mapping();
			if ( !mapping.isEmpty() ) {
				return mapping;
			}

			// Otherwise we'll try to use REST to get the info and match, but that may not necessarily work fine
			List<JiraSimpleObject> source = context.sourceJiraClient().getIssueTypes();
			List<JiraSimpleObject> destination = context.destinationJiraClient().getIssueTypes();

			return createMapping( source, destination );

		}, mappedValues.defaultValue() ) );
	}

	protected Optional<String> statusToTransition(String sourceId) {
		JiraConfig.ValueMapping mappedValues = context.projectGroup().statuses();
		return Optional.ofNullable( JiraStaticFieldMappingCache.status( context.projectGroupName(), sourceId, pk -> {
			Map<String, String> mapping = context.projectGroup().statuses().mapping();
			if ( !mapping.isEmpty() ) {
				return mapping;
			}

			// Otherwise we'll try to use REST to get the info and match, but that may not necessarily work fine
			List<JiraSimpleObject> source = context.sourceJiraClient().getStatues().values;
			List<JiraSimpleObject> destination = context.destinationJiraClient().getStatues().values;

			return createMapping( source, destination );
		}, mappedValues.defaultValue() ) );
	}

	protected Optional<String> linkType(String sourceId) {
		JiraConfig.ValueMapping mappedValues = context.projectGroup().issueLinkTypes();
		return Optional.ofNullable( JiraStaticFieldMappingCache.linkType( context.projectGroupName(), sourceId, pk -> {
			Map<String, String> mapping = context.projectGroup().issueTypes().mapping();
			if ( !mapping.isEmpty() ) {
				return mapping;
			}

			// Otherwise we'll try to use REST to get the info and match, but that may not necessarily work fine
			List<JiraSimpleObject> source = context.sourceJiraClient().getIssueLinkTypes().issueLinkTypes;
			List<JiraSimpleObject> destination = context.destinationJiraClient().getIssueLinkTypes().issueLinkTypes;

			return createMapping( source, destination );
		}, mappedValues.defaultValue() ) );
	}

	protected Optional<String> user(JiraUser sourceUser) {
		if ( sourceUser == null ) {
			return Optional.empty();
		}
		return Optional.ofNullable( JiraStaticFieldMappingCache.user( context.projectGroupName(), sourceUser.accountId, userId -> context.projectGroup().users().mapping().get( sourceUser.accountId ) ) );
	}

	private static Map<String, String> createMapping(List<JiraSimpleObject> source, List<JiraSimpleObject> destination) {
		Map<String, String> mapping = new HashMap<>();
		for ( JiraSimpleObject p : source ) {
			for ( JiraSimpleObject d : destination ) {
				if ( p.name.equals( d.name ) ) {
					mapping.put( p.id, d.id );
					break;
				}
			}
		}
		return mapping;
	}

	@Override
	public final void run() {
		try {
			doRun();
		}
		catch (RuntimeException e) {
			failureCollector.critical( "Failed to handled the event", e );
		}
		finally {
			failureCollector.close();
		}
	}

	protected abstract void doRun();

	protected JiraIssue getDestinationIssue(String key) {
		Optional<JiraIssue> jiraIssue = find( key );

		int attempt = 0;
		while ( jiraIssue.isEmpty() && attempt < MAX_ATTEMPTS ) {
			// try creating placeholders until we create enough so that the key is available,
			// but no more than some limit
			jiraIssue = createIssuePlaceholder( key );
			attempt++;
		}
		return jiraIssue.orElseThrow( () -> new JiraRestException( "Unable to create the placeholder for the %s in %d attempt(s).".formatted( key, MAX_ATTEMPTS ), 500 ) );
	}

	private Optional<JiraIssue> find(String key) {
		try {
			return Optional.of( context.destinationJiraClient().getIssue( key ) );
		}
		catch (JiraRestException e) {
			return Optional.empty();
		}
	}

	private Long keyToLong(String key) {
		return Long.parseLong( key.substring( key.lastIndexOf( '-' ) + 1 ) );
	}

	protected String toDestinationKey(String key) {
		if ( keyToUpdatePattern.matcher( key ).matches() ) {
			return "%s-%d".formatted( context.project().projectKey(), keyToLong( key ) );
		}
		return key;
	}

	private Optional<JiraIssue> createIssuePlaceholder(String expectedKey) {
		JiraIssue placeholder = new JiraIssue();
		placeholder.fields = new JiraFields();
		placeholder.fields.summary = "Sync issue placeholder";

		placeholder.fields.description = "This is a placeholder issue. It will be updated at a later point in time. DO NOT EDIT.";

		placeholder.fields.project.id = context.project().projectId();
		placeholder.fields.issuetype.id = context.projectGroup().issueTypes().defaultValue();

		// just to be sure that these are not sent as part of
		//  the placeholder request to keep it as simple as it can be:
		placeholder.fields.reporter = null;
		placeholder.fields.assignee = null;
		placeholder.fields.priority = null;

		try {
			// TODO: consider using the bulk api to create multiple issues at a time,
			//  we'd need them later anyways, so maybe worth not doing it one at a time but 5-10?
			JiraIssueResponse issueResponse = context.destinationJiraClient().create( placeholder );
			if ( keyToLong( issueResponse.key ) >= keyToLong( expectedKey ) ) {
				return Optional.of( context.destinationJiraClient().getIssue( expectedKey ) );
			}
		}
		catch (JiraRestException e) {
			failureCollector.warning( "Failed creating an issue placeholder: %s".formatted( e.getMessage() ), e );
		}

		return Optional.empty();
	}
}
