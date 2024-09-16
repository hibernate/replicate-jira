package org.hibernate.infra.sync.jira.service.jira.handler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.infra.sync.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComment;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraComments;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraTextBody;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraTextContent;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

abstract class JiraCommentEventHandler extends JiraEventHandler {
	// this is the API default value so let's start with it and may change later if needed:
	protected static final int MAX_COMMENTS_RESULTS = 5000;

	protected final Long issueId;

	public JiraCommentEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long commentId, Long issueId) {
		super( reportingConfig, context, commentId );
		this.issueId = issueId;
	}

	protected Optional<JiraComment> findComment(JiraComment comment, JiraComments comments) {
		return findComment( comment.id, comments );
	}

	protected Optional<JiraComment> findComment(String commentId, JiraComments comments) {
		if ( comments.comments == null || comments.comments.isEmpty() ) {
			return Optional.empty();
		}
		for ( JiraComment check : comments.comments ) {
			if ( hasRequiredCommentQuote( check.body, commentId ) ) {
				return Optional.of( check );
			}
		}
		return Optional.empty();
	}

	@SuppressWarnings("unchecked")
	private boolean hasRequiredCommentQuote(JiraTextBody body, String commentId) {
		if ( body.content == null || body.content.size() < 2 ) {
			return false;
		}
		JiraTextContent quote = body.content.get( 0 );
		if ( "blockquote".equals( quote.type ) ) {
			try {
				List<Map<String, Object>> content = (List<Map<String, Object>>) quote.properties().get( "content" );
				if ( content.size() != 1 ) { // paragraph
					return false;
				}
				Map<String, Object> paragraph = content.get( 0 );
				Map<String, Object> upstreamCommentLink = ( (List<Map<String, Object>>) paragraph.get( "content" ) ).get( 0 );
				List<Map<String, Object>> marks = (List<Map<String, Object>>) upstreamCommentLink.get( "marks" );
				if ( marks != null && marks.size() == 1 ) {
					Map<String, Object> attrs = (Map<String, Object>) marks.get( 0 ).get( "attrs" );
					if ( attrs != null ) {
						String link = attrs.get( "href" ).toString();
						if ( link != null && link.endsWith( "focusedCommentId=" + commentId ) ) {
							return true;
						}
					}
				}
			}
			catch (Exception e) {
				return false;
			}
		}
		return false;
	}
}
