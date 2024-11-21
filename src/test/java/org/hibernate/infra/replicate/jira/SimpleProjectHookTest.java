package org.hibernate.infra.replicate.jira;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import org.hibernate.infra.replicate.jira.service.validation.RequestSignatureFilter;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class SimpleProjectHookTest {

	private static final String REQUEST_BODY = """
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
			""";

	@Test
	void unknown() {
		given().when().body(REQUEST_BODY).contentType(ContentType.JSON).post("api/jira/webhooks/NOTAPROJECTKEY").then()
				.statusCode(400).body(containsString("The NOTAPROJECTKEY project is not configured"));
	}

	@Test
	void known() {
		given().when().body(REQUEST_BODY)
				.header("x-hub-signature", RequestSignatureFilter.sign("not-a-secret", REQUEST_BODY))
				.contentType(ContentType.JSON).post("api/jira/webhooks/JIRATEST1").then().statusCode(200)
				.body(is("ack"));
	}

	@Test
	void knownNoHeader() {
		given().when().body(REQUEST_BODY).contentType(ContentType.JSON).post("api/jira/webhooks/JIRATEST1").then()
				.statusCode(401);
	}

}
