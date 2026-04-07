package org.hibernate.infra.replicate.jira.service.jira.model.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class JiraCommentTest {

	@Test
	void removeAtMentions() {
		URI someUri = URI.create("https://hibernate.atlassian.net/rest/api/2/issue/JIRATEST1-26/comment");
		assertThat(JiraComment.removeAtMentions(someUri, "Hello [~accountid:user123]"))
				.isEqualTo("Hello [@ user id(user123)|https://hibernate.atlassian.net/jira/people/user123]");
		assertThat(JiraComment.removeAtMentions(someUri,
				"Hello [~accountid:user123] and [~accountid:something-else-here-123]")).isEqualTo(
						"Hello [@ user id(user123)|https://hibernate.atlassian.net/jira/people/user123] and [@ user id(something-else-here-123)|https://hibernate.atlassian.net/jira/people/something-else-here-123]");
	}
}
