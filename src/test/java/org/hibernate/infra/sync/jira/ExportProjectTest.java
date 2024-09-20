package org.hibernate.infra.sync.jira;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.infra.sync.jira.service.jira.client.JiraRestClient;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestClientBuilder;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssues;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraSimpleObject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

@QuarkusTest
class ExportProjectTest {

	private static final int MAX_LABELS_LIMIT = 10;
	// in the service we map status id to a transition, but with the export we need to have name-to-name mapping instead:
	private static final Map<String, String> statusMap;
	private static final Map<String, String> issueTypeMap;

	static {
		statusMap = new HashMap<>();
		statusMap.put( "Awaiting Test Case", "Open" );
		statusMap.put( "Awaiting Response", "Open" );
		statusMap.put( "Waiting for review", "Open" );
		statusMap.put( "Awaiting Contribution", "Open" );
		statusMap.put( "Reopened", "Reopened" );
		statusMap.put( "To Do", "To Do" );
		statusMap.put( "In Progress", "In Progress" );
		statusMap.put( "Stalled", "Stalled" );
		statusMap.put( "In Review", "In Review" );
		statusMap.put( "Review", "In Review" );
		statusMap.put( "Resolved", "Resolved" );
		statusMap.put( "Closed", "Closed" );
		statusMap.put( "Done", "Done" );

		issueTypeMap = new HashMap<>();

		issueTypeMap.put( "Bug", "Bug");
		issueTypeMap.put( "New Feature", "New Feature");
		issueTypeMap.put( "Task", "Task");
		issueTypeMap.put( "Improvement", "Improvement");
		issueTypeMap.put( "Patch", "Patch");
		issueTypeMap.put( "Deprecation", "");
		issueTypeMap.put( "Sub-task", "Sub-task");
		issueTypeMap.put( "Subtask", "Sub-task");
		issueTypeMap.put( "Remove Feature", "Task");
		issueTypeMap.put( "Technical task", "Technical task");
		issueTypeMap.put( "Epic", "Epic");
		issueTypeMap.put( "Story", "Story");
		issueTypeMap.put( "Proposal", "Task");
	}

	@Inject
	JiraConfig jiraConfig;

	@Disabled
	@Test
	void export() throws IOException {
		JiraConfig.JiraProjectGroup projectGroup = jiraConfig.projectGroup().get( "hibernate" );
		JiraRestClient source = JiraRestClientBuilder.of( projectGroup.source() );

		List<String> headers = new ArrayList<>( List.of( "key", "issue type", "status", "project key", "project name", "reporter", "assignee", "summary", "description" ) );

		for ( int i = 0; i < MAX_LABELS_LIMIT; i++ ) {
			headers.add( "labels" );
		}

		CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
				.setHeader( headers.toArray( String[]::new ) )
				.build();

		int start = 0;
		int max = 100; // this seems to be a limit on the jira side.. requesting more didn't work.


		JiraIssues issues;

		try ( final FileWriter fw = new FileWriter( "target/HV.csv" ); final CSVPrinter printer = new CSVPrinter( fw, csvFormat ) ) {
			do {
				issues = source.find( "project=10060 ORDER BY key", start, max );
				for ( JiraIssue issue : issues.issues ) {
					List<Object> row = new ArrayList<>();
					row.add( issue.key );
					row.add( issueTypeMap.getOrDefault( issue.fields.issuetype.name, "" ) );
					row.add( statusMap.getOrDefault( issue.fields.status.name, "" ) );
					row.add( issue.fields.project.properties().get( "key" ) );
					row.add( issue.fields.project.name );
					row.add( issue.fields.reporter == null ? null : projectGroup.users().mapping().getOrDefault( issue.fields.reporter.accountId, null ) );
					row.add( issue.fields.assignee == null ? null : projectGroup.users().mapping().getOrDefault( issue.fields.assignee.accountId, null ) );
					row.add( issue.fields.summary );
					row.add( issue.fields.description );
					row.add( issue.key );
					row.addAll( formatLabels( issue ) );

					printer.printRecord( row );
				}
				start += max;
			} while ( !issues.issues.isEmpty() );
		}
	}

	private Map<String, String> statusIdToName(JiraRestClient destination, JiraConfig.JiraProjectGroup projectGroup) {
		return mapToName( destination.getStatues().values, projectGroup.statuses().mapping() );
	}

	private Map<String, String> issueTypeIdToName(JiraRestClient destination, JiraConfig.JiraProjectGroup projectGroup) {
		return mapToName( destination.getIssueTypes(), projectGroup.issueTypes().mapping() );
	}

	private Map<String, String> mapToName(List<JiraSimpleObject> values, Map<String, String> mapping) {
		Map<String, String> result = new HashMap<>();
		for ( var entry : mapping.entrySet() ) {
			for ( JiraSimpleObject value : values ) {
				if ( entry.getValue().equals( value.id ) ) {
					result.put( entry.getKey(), value.name );
				}
			}
		}

		return result;
	}

	private List<String> formatLabels(JiraIssue issue) {
		List<String> labels = new ArrayList<>();
		if ( issue.fields.labels != null && !issue.fields.labels.isEmpty() ) {
			labels.addAll( issue.fields.labels );
		}

		if ( issue.fields.fixVersions != null ) {
			for ( JiraSimpleObject fixVersion : issue.fields.fixVersions ) {
				labels.add( "Fix version: %s".formatted( fixVersion.name ) );
			}
		}

		for ( int i = labels.size(); i < MAX_LABELS_LIMIT; i++ ) {
			labels.add( "" );
		}

		return labels.subList( 0, MAX_LABELS_LIMIT );
	}
}
