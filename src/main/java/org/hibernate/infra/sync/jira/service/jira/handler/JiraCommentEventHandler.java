package org.hibernate.infra.sync.jira.service.jira.handler;

import java.util.Optional;
import java.util.regex.Pattern;

import org.hibernate.infra.sync.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComment;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComments;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

abstract class JiraCommentEventHandler extends JiraEventHandler {
	// this is the API default value so let's start with it and may change later if
	// needed:
	protected static final int MAX_COMMENTS_RESULTS = 5000;

	protected final Long issueId;

	public JiraCommentEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long commentId,
			Long issueId) {
		super(reportingConfig, context, commentId);
		this.issueId = issueId;
	}

	protected Optional<JiraComment> findComment(JiraComment comment, JiraComments comments) {
		return findComment(comment.id, comments);
	}

	protected Optional<JiraComment> findComment(String commentId, JiraComments comments) {
		if (comments.comments == null || comments.comments.isEmpty()) {
			return Optional.empty();
		}
		for (JiraComment check : comments.comments) {
			if (hasRequiredCommentQuote(check.body, commentId)) {
				return Optional.of(check);
			}
		}
		return Optional.empty();
	}

	private boolean hasRequiredCommentQuote(String body, String commentId) {
		return Pattern.compile("(?s)^\\{quote\\}This \\[comment.+\\?focusedCommentId=%s\\].*".formatted(commentId))
				.matcher(body).matches();
	}
}
