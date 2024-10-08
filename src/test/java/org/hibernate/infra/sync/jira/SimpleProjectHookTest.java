package org.hibernate.infra.sync.jira;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class SimpleProjectHookTest {
	@Test
	void unknown() {
		given().when()
				.body("""
						{
						"timestamp": 1727087443918,
						"webhookEvent": "jira:issue_updated",
						"issue_event_type_name": "issue_generic",
						"user": {
							"self": "https://hibernate.atlassian.net/rest/api/2/user?accountId=557058%3A18cf44bb-bc9b-4e8d-b1b7-882969ddc8e5",
							"accountId": "557058:18cf44bb-bc9b-4e8d-b1b7-882969ddc8e5"
						},
						"issue": {
							"id": "77256",
							"self": "https://hibernate.atlassian.net/rest/api/2/77256",
							"key": "JIRATEST1-14"
						}
						}
						""")
				.contentType(ContentType.JSON).post("api/jira/webhooks/NOTAPROJECTKEY").then().statusCode(500)
				.body(containsString(
						"Unable to determine handler context for project NOTAPROJECTKEY. Was it not configured"));
	}

	@Test
	void known() {
		given().when()
				.body("""
						{
						"timestamp": 1727087443918,
						"webhookEvent": "jira:issue_updated",
						"issue_event_type_name": "issue_generic",
						"user": {
							"self": "https://hibernate.atlassian.net/rest/api/2/user?accountId=557058%3A18cf44bb-bc9b-4e8d-b1b7-882969ddc8e5",
							"accountId": "557058:18cf44bb-bc9b-4e8d-b1b7-882969ddc8e5"
						},
						"issue": {
							"id": "1",
							"self": "https://hibernate.atlassian.net/rest/api/2/1",
							"key": "JIRATEST1-1"
						}
						}
						""")
				.contentType(ContentType.JSON).post("api/jira/webhooks/JIRATEST1").then().statusCode(200)
				.body(is("ack"));
	}

}
