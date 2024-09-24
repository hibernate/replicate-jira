package org.hibernate.infra.sync.jira.service.jira.handler;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.infra.sync.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraFields;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

public class JiraIssueDeleteEventHandler extends JiraEventHandler {
	private final String key;

	public JiraIssueDeleteEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id, String key) {
		super( reportingConfig, context, id );
		this.key = key;
	}

	@Override
	protected void doRun() {
		// TODO: do we actually want to delete the issue ? or maybe let's instead add a label,
		//  and update the summary, saying that the issue is deleted:

		// first let's make sure that the issue is actually deleted upstream:
		try {
			// Note: we do the search based on the jira key as we want to make sure
			// that such key does not exist, searching by ID may also not find the issue,
			// but then if the issue is not there we cannot check that the key matches the ID.
			context.sourceJiraClient().getIssue( key );

			// if the issue is deleted then we should get a 404 and never reach this line:
			failureCollector.critical( "Request to delete an issue %s that is actually not deleted".formatted( key ) );
			return;
		}
		catch (JiraRestException e) {
			// all good the issue is not available let's mark the other one as deleted now:

			try {
				String destinationKey = toDestinationKey( key );
				JiraIssue issue = context.destinationJiraClient().getIssue( destinationKey );
				JiraIssue updated = new JiraIssue();

				updated.fields = new JiraFields();
				updated.fields.summary = "DELETED upstream: " + issue.fields.summary;
				if ( issue.fields.labels == null ) {
					issue.fields.labels = List.of();
				}
				ArrayList<String> updatedLabels = new ArrayList<>( issue.fields.labels );
				updatedLabels.add( "Deleted Upstream" );
				updated.fields.labels = updatedLabels;

				context.destinationJiraClient().update( destinationKey, updated );
			}
			catch (Exception ex) {
				failureCollector.critical( "Unable to mark the issue %s as deleted: %s".formatted( objectId, ex.getMessage() ), ex );
			}
		}
	}
}
