package org.hibernate.infra.replicate.jira.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.hibernate.infra.replicate.jira.JiraConfig;
import org.hibernate.infra.replicate.jira.mock.SampleJiraRestClient;
import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectGroupContext;
import org.hibernate.infra.replicate.jira.service.jira.handler.JiraCommentDeleteEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.handler.JiraCommentUpsertEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.handler.JiraIssueDeleteEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.handler.JiraIssueLinkDeleteEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.handler.JiraIssueLinkUpsertEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.handler.JiraIssueUpsertEventHandler;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.mockito.Mockito;

@QuarkusTest
class IssueTest {

	private static final String PROJECT_GROUP_NAME = "hibernate";
	@InjectSpy
	SampleJiraRestClient source;

	@InjectSpy
	SampleJiraRestClient destination;

	@Inject
	ReportingConfig reportingConfig;

	@Inject
	JiraConfig jiraConfig;

	HandlerProjectGroupContext context;

	@BeforeEach
	void setUp() {
		context = new HandlerProjectGroupContext(PROJECT_GROUP_NAME, jiraConfig.projectGroup().get(PROJECT_GROUP_NAME),
				source, destination);
	}

	@AfterEach
	void tearDown() {
		context.close();
	}

	@Test
	void testUpsert() {
		long issueId = 1L;
		new JiraIssueUpsertEventHandler(reportingConfig, context, issueId).run();

		// we expect that
		// - the downstream issue is updated,
		// - web link added pointing to the issue
		// - transition is performed
		Mockito.verify(destination, Mockito.times(1)).update(eq("JIRATEST2-1"), any(JiraIssue.class));
		Mockito.verify(destination, Mockito.times(1)).upsertRemoteLink(eq("JIRATEST2-1"), any());
		Mockito.verify(destination, Mockito.times(1)).transition(eq("JIRATEST2-1"), any());
	}

	@Test
	void testRemoveExisting() {
		long issueId = 1L;
		String key = "JIRATEST1-1";
		assertThatThrownBy(() -> new JiraIssueDeleteEventHandler(reportingConfig, context, issueId, key).run())
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Request to delete an issue JIRATEST1-1 that is actually not deleted");

		// we expect that
		// - we called the source jira and it didn't throw, which should mean that the
		// issue actually still exists in the original JIRA
		Mockito.verify(source, Mockito.times(1)).getIssue(eq("JIRATEST1-1"));
	}

	@Test
	void testRemoveNonExisting() {
		try {
			source.itemCannotBeFound.set("JIRATEST1-1");
			long issueId = 1L;
			String key = "JIRATEST1-1";
			new JiraIssueDeleteEventHandler(reportingConfig, context, issueId, key).run();

			// we expect that
			// - we called the source jira and it throws 404
			// - destination jira is updated (title)
			Mockito.verify(source, Mockito.times(1)).getIssue(eq("JIRATEST1-1"));
			Mockito.verify(destination, Mockito.times(1)).update(eq("JIRATEST2-1"), any(JiraIssue.class));
		} finally {
			source.itemCannotBeFound.set("");
		}
	}

	@Test
	void testAddComment() {
		new JiraCommentUpsertEventHandler(reportingConfig, context, 1L, 2L).run();

		// we expect that
		// - we looked for an original comment first
		// - then tried to find comments
		// - then add/update the comment
		Mockito.verify(source, Mockito.times(1)).getIssue(eq(2L));
		Mockito.verify(source, Mockito.times(1)).getComment(eq(2L), eq(1L));
		Mockito.verify(destination, Mockito.times(1)).getComments(eq("JIRATEST2-2"), eq(0), eq(5000));
		Mockito.verify(destination, Mockito.times(1)).create(eq("JIRATEST2-2"), any());
	}

	@Test
	void testRemoveExistingComment() {
		new JiraCommentDeleteEventHandler(reportingConfig, context, 1L, 2L).run();

		// we expect that
		// - we looked for an original comment first and found it, so we do not remove
		// anything
		Mockito.verify(source, Mockito.times(1)).getIssue(eq(2L));
		Mockito.verify(source, Mockito.times(1)).getComment(eq(2L), eq(1L));
		Mockito.verify(destination, Mockito.never()).deleteComment(eq("JIRATEST2-2"), any());
	}

	@Test
	void testRemoveNonExistingComment() {
		try {
			long commentId = 123L;
			long issueId = 2L;
			source.itemCannotBeFound.set("%d - %d".formatted(issueId, commentId));
			new JiraCommentDeleteEventHandler(reportingConfig, context, commentId, issueId).run();

			// we expect that
			// - we looked for an original comment first and did not find it
			// - so we remove the downstream comment
			Mockito.verify(source, Mockito.times(1)).getIssue(eq(issueId));
			Mockito.verify(source, Mockito.times(1)).getComment(eq(issueId), eq(commentId));
			Mockito.verify(destination, Mockito.times(1)).getComments(eq("JIRATEST2-2"), eq(0), eq(5000));
			Mockito.verify(destination, Mockito.times(1)).deleteComment(eq("JIRATEST2-2"), any());
		} finally {
			source.itemCannotBeFound.set("");
		}
	}

	@Test
	void testIssueLinkCreated() {
		try {
			destination.hasIssueLinks.set(false);
			new JiraIssueLinkUpsertEventHandler(reportingConfig, context, 1L).run();

			// we expect that
			// - we looked an issue link first
			// - then we looked up one sides of the link to get the issue and see if there
			// is already a link like that created.
			// - and then created an issue link:
			Mockito.verify(source, Mockito.times(1)).getIssueLink(eq(1L));
			Mockito.verify(destination, Mockito.times(1)).getIssue(eq("JIRATEST2-1"));
			Mockito.verify(destination, Mockito.times(1)).upsertIssueLink(any());
		} finally {
			destination.hasIssueLinks.set(true);
		}
	}

	@Test
	void testIssueLinkDelete() {
		// Long id, Long sourceIssueId, Long destinationIssueId, String issueLinkTypeId
		new JiraIssueLinkDeleteEventHandler(reportingConfig, context, 1L, 3L, 7L, "10100").run();

		// we expect that
		// - we look up both sides of the link in the source jira to get the keys
		// - then we looked up both sides of the link on the destination side
		// - and then delete an issue link:
		Mockito.verify(source, Mockito.times(1)).getIssue(eq(3L));
		Mockito.verify(source, Mockito.times(1)).getIssue(eq(7L));
		Mockito.verify(destination, Mockito.times(1)).getIssue(eq("JIRATEST2-3"));
		Mockito.verify(destination, Mockito.times(1)).getIssue(eq("JIRATEST2-7"));
		Mockito.verify(destination, Mockito.times(1)).deleteIssueLink(any());
	}
}
