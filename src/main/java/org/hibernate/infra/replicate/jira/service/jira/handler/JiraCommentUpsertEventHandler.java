package org.hibernate.infra.replicate.jira.service.jira.handler;

import java.net.URI;
import java.util.Optional;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraComment;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraComments;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraCommentUpsertEventHandler extends JiraCommentEventHandler {

	public JiraCommentUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectGroupContext context,
			Long commentId, Long issueId) {
		super(reportingConfig, context, commentId, issueId);
	}

	@Override
	protected void doRun() {
		JiraIssue issue = context.sourceJiraClient().getIssue(issueId);
		JiraComment comment = context.sourceJiraClient().getComment(issueId, objectId);

		String destinationKey = context.contextForOriginalProjectKey(toProjectFromKey(issue.key))
				.toDestinationKey(issue.key);

		// We are going to assume that the Jira issue was already synced downstream,
		// and try to find its comments. If the issue is not yet there, then we'll just
		// fail here:
		JiraComments destinationComments = null;
		try {
			destinationComments = context.destinationJiraClient().getComments(destinationKey, 0, MAX_COMMENTS_RESULTS);
		} catch (JiraRestException e) {
			failureCollector.critical("Failed to find an issue " + destinationKey
					+ " in the destination Jira. Unable to sync the comments.", e);
			return;
		}

		Optional<JiraComment> destComment = findComment(comment, destinationComments);
		if (destComment.isPresent()) {
			String commentId = destComment.get().id;
			context.destinationJiraClient().update(destinationKey, commentId, prepareComment(issue, comment));
		} else {
			context.destinationJiraClient().create(destinationKey, prepareComment(issue, comment));
		}
	}

	private JiraComment prepareComment(JiraIssue issue, JiraComment source) {
		JiraComment comment = new JiraComment();
		// we add the quote as a first element, and then follow it up with the original
		// comment content:
		comment.body = prepareCommentQuote(issue, source) + source.body;

		return comment;
	}

	private String prepareCommentQuote(JiraIssue issue, JiraComment comment) {
		URI jiraCommentUri = createJiraCommentUri(issue, comment);
		UserData userData = userData(comment.self, comment.author, "the user %s");
		UserData editUserData = userData(comment.self, comment.updateAuthor, "the user %s");
		String content = """
				{quote}This [comment|%s] was posted by [%s|%s] on %s.%s{quote}


				""".formatted(jiraCommentUri, userData.name(), userData.uri(), context.formatTimestamp(comment.created),
				comment.isUpdatedSameAsCreated()
						? ""
						: """

								[%s|%s] edited the comment on %s.
								""".formatted(editUserData.name(), editUserData.uri(),
								context.formatTimestamp(comment.updated)));
		return truncateContent(content);
	}

	@Override
	public String toString() {
		return "JiraCommentUpsertEventHandler[" + "issueId=" + issueId + ", objectId=" + objectId + ", projectGroup="
				+ context.projectGroupName() + ']';
	}
}
