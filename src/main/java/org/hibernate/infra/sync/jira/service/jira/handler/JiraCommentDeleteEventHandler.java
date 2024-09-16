package org.hibernate.infra.sync.jira.service.jira.handler;

import java.util.Optional;

import org.hibernate.infra.sync.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComment;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComments;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

public class JiraCommentDeleteEventHandler extends JiraCommentEventHandler {
	public JiraCommentDeleteEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long commentId, Long issueId) {
		super( reportingConfig, context, commentId, issueId );
	}

	@Override
	protected void doRun() {
		JiraIssue issue = context.sourceJiraClient().getIssue( issueId );
		try {
			JiraComment comment = context.sourceJiraClient().getComment( issueId, objectId );
		}
		catch (JiraRestException e) {
			if ( e.statusCode() == 404 ) {
				// all good comment is deleted
				JiraComments comments = context.destinationJiraClient().getComments( issue.key, 0, MAX_COMMENTS_RESULTS );

				Optional<JiraComment> found = findComment( Long.toString( objectId ), comments );

				found.ifPresent( jiraComment -> context.destinationJiraClient().deleteComment( issue.key, jiraComment.id ) );
			}
			else {
				throw e;
			}
		}
	}
}
