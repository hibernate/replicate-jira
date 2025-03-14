package org.hibernate.infra.replicate.jira.service.jira.handler;

import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraComment;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraComments;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraCommentDeleteEventHandler extends JiraCommentEventHandler {
	public JiraCommentDeleteEventHandler(ReportingConfig reportingConfig, HandlerProjectGroupContext context,
			Long commentId, Long issueId) {
		super(reportingConfig, context, commentId, issueId);
	}

	@Override
	protected void doRun() {
		JiraIssue issue = context.sourceJiraClient().getIssue(issueId);
		String destinationKey = context.contextForOriginalProjectKey(toProjectFromKey(issue.key))
				.toDestinationKey(issue.key);
		try {
			JiraComment comment = context.sourceJiraClient().getComment(issueId, objectId);
		} catch (JiraRestException e) {
			if (e.statusCode() == 404) {
				// all good comment is deleted
				JiraComments comments = context.destinationJiraClient().getComments(destinationKey, 0,
						MAX_COMMENTS_RESULTS);

				Optional<JiraComment> found = findComment(Long.toString(objectId), comments);

				found.ifPresent(
						jiraComment -> context.destinationJiraClient().deleteComment(destinationKey, jiraComment.id));
			} else {
				throw e;
			}
		}
	}

	@Override
	public String toString() {
		return "JiraCommentDeleteEventHandler[" + "issueId=" + issueId + ", objectId=" + objectId + ", projectGroup="
				+ context.projectGroupName() + ']';
	}
}
