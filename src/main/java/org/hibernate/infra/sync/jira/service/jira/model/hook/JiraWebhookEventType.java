package org.hibernate.infra.sync.jira.service.jira.model.hook;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.hibernate.infra.sync.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.sync.jira.service.jira.handler.JiraCommentDeleteEventHandler;
import org.hibernate.infra.sync.jira.service.jira.handler.JiraCommentUpsertEventHandler;
import org.hibernate.infra.sync.jira.service.jira.handler.JiraIssueDeleteEventHandler;
import org.hibernate.infra.sync.jira.service.jira.handler.JiraIssueLinkDeleteEventHandler;
import org.hibernate.infra.sync.jira.service.jira.handler.JiraIssueLinkUpsertEventHandler;
import org.hibernate.infra.sync.jira.service.jira.handler.JiraIssueUpsertEventHandler;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

public enum JiraWebhookEventType {
	ISSUE_CREATED( "jira:issue_created" ) {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context) {
			if ( event.issue == null || event.issue.id == null ) {
				throw new IllegalStateException( "Trying to handle an issue event but issue id is null: %s".formatted( event ) );
			}
			return List.of( new JiraIssueUpsertEventHandler( reportingConfig, context, event.issue.id ) );
		}
	},
	ISSUE_UPDATED( "jira:issue_updated" ) {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context) {
			if ( event.issue == null || event.issue.id == null ) {
				throw new IllegalStateException( "Trying to handle an issue event but issue id is null: %s".formatted( event ) );
			}
			return List.of( new JiraIssueUpsertEventHandler( reportingConfig, context, event.issue.id ) );
		}
	},
	ISSUE_DELETED( "jira:issue_deleted" ) {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context) {
			if ( event.issue == null || event.issue.id == null ) {
				throw new IllegalStateException( "Trying to handle an issue event but issue id is null: %s".formatted( event ) );
			}
			return List.of( new JiraIssueDeleteEventHandler( reportingConfig, context, event.issue.id, event.issue.key ) );
		}
	},
	ISSUELINK_CREATED( "issuelink_created" ) {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context) {
			if ( event.issueLink == null || event.issueLink.sourceIssueId == null ) {
				throw new IllegalStateException( "Trying to handle an issue link event but source issue id is null: %s".formatted( event ) );
			}
			// NOTE: for linking issues we will just trigger an "issue update" handling instead of doing something special (at least for now):
			return List.of( new JiraIssueLinkUpsertEventHandler( reportingConfig, context, event.issueLink.id ) );
		}
	},
	ISSUELINK_DELETED( "issuelink_deleted" ) {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context) {
			if ( event.issueLink == null || event.issueLink.sourceIssueId == null ) {
				throw new IllegalStateException( "Trying to handle an issue link event but source issue id is null: %s".formatted( event ) );
			}
			// NOTE: for linking issues we will just trigger an "issue update" handling instead of doing something special (at least for now):
			return List.of( new JiraIssueLinkDeleteEventHandler( reportingConfig, context, event.issueLink.id, event.issueLink.sourceIssueId, event.issueLink.destinationIssueId, event.issueLink.issueLinkType.id ) );
		}
	},
	COMMENT_CREATED( "comment_created" ) {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context) {
			if ( event.comment == null || event.comment.id == null ) {
				throw new IllegalStateException( "Trying to handle a comment event but comment id is null: %s".formatted( event ) );
			}
			if ( event.issue == null || event.issue.id == null ) {
				throw new IllegalStateException( "Trying to handle a comment event but issue id is null: %s".formatted( event ) );
			}
			return List.of( new JiraCommentUpsertEventHandler( reportingConfig, context, event.comment.id, event.issue.id ) );
		}
	},
	COMMENT_UPDATED( "comment_updated" ) {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context) {
			if ( event.comment == null || event.comment.id == null ) {
				throw new IllegalStateException( "Trying to handle a comment event but comment id is null: %s".formatted( event ) );
			}
			if ( event.issue == null || event.issue.id == null ) {
				throw new IllegalStateException( "Trying to handle a comment event but issue id is null: %s".formatted( event ) );
			}
			return List.of( new JiraCommentUpsertEventHandler( reportingConfig, context, event.comment.id, event.issue.id ) );
		}
	},
	COMMENT_DELETED( "comment_deleted" ) {
		@Override
		public Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context) {
			if ( event.comment == null || event.comment.id == null ) {
				throw new IllegalStateException( "Trying to handle a comment event but comment id is null: %s".formatted( event ) );
			}
			return List.of( new JiraCommentDeleteEventHandler( reportingConfig, context, event.comment.id, event.issue.id ) );
		}
	};

	private final String name;

	JiraWebhookEventType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public static Optional<JiraWebhookEventType> of(String webhookEvent) {
		if ( webhookEvent == null ) {
			return Optional.empty();
		}
		for ( JiraWebhookEventType value : values() ) {
			if ( value.name.equals( webhookEvent.toLowerCase( Locale.ROOT ) ) ) {
				return Optional.of( value );
			}
		}
		return Optional.empty();
	}

	public abstract Collection<Runnable> handlers(ReportingConfig reportingConfig, JiraWebHookEvent event, HandlerProjectContext context);
}
