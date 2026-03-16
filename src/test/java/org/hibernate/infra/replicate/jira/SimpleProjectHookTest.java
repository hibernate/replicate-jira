package org.hibernate.infra.replicate.jira;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import java.util.Map;

import org.hibernate.infra.replicate.jira.service.validation.RequestSignatureFilter;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.logging.Log;
import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTestResource(value = SimpleProjectHookTest.InitializedWireMockServerConnector.class, restrictToAnnotatedClass = false)
@QuarkusTest
class SimpleProjectHookTest {

	public static class InitializedWireMockServerConnector
			implements
				QuarkusTestResourceLifecycleManager,
				DevServicesContext.ContextAware {
		WireMock wiremock;

		@Override
		public void setIntegrationTestContext(DevServicesContext context) {
			Map<String, String> devContext = context.devServicesProperties();
			try {
				int port = Integer.parseInt(devContext.get("quarkus.wiremock.devservices.port"));
				this.wiremock = new WireMock(port);
				WireMock.configureFor(port);
				this.wiremock.getGlobalSettings();
			} catch (Exception ex) {
				Log.error("Cannot connect to WireMock server!", ex);
				throw ex;
			}

			wiremock.register(WireMock.get(WireMock.urlPathTemplate("/api/jira-mock-api/project/JIRATEST1"))
					.willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
							.withBody(PROJECT_RESPONSE1)));
			wiremock.register(WireMock.get(WireMock.urlPathTemplate("/api/jira-mock-api/project/JIRATEST2"))
					.willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
							.withBody(PROJECT_RESPONSE2)));
			wiremock.register(WireMock.get(WireMock.urlPathTemplate("/api/jira-mock-api/project/10323"))
					.willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
							.withBody(PROJECT_RESPONSE2)));
			wiremock.register(WireMock.get(WireMock.urlPathTemplate("/api/jira-mock-api/project/10324"))
					.willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
							.withBody(PROJECT_RESPONSE1)));
		}

		@Override
		public Map<String, String> start() {
			return Map.of();
		}

		@Override
		public void stop() {

		}
	}

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

	private static final String PROJECT_RESPONSE1 = """
			{
			"self": "https://redhat.atlassian.net/rest/api/2/project/10324",
			"id": "10324",
			"key": "JIRATEST1",
			"description": "Test project"
			}
			""";
	private static final String PROJECT_RESPONSE2 = """
			{
			"self": "https://redhat.atlassian.net/rest/api/2/project/10323",
			"id": "10323",
			"key": "JIRATEST2",
			"description": "Test project"
			}
			""";

	@Test
	void unknown() {
		given().when().body(REQUEST_BODY).contentType(ContentType.JSON)
				.post("api/jira/webhooks/source/NOTAPROJECTGROUPKEY").then().statusCode(400)
				.body(containsString("The NOTAPROJECTGROUPKEY project group is not configured"));
	}

	@Test
	void known() {
		given().when().body(REQUEST_BODY)
				.header("x-hub-signature", RequestSignatureFilter.sign("not-a-secret", REQUEST_BODY))
				.contentType(ContentType.JSON).post("api/jira/webhooks/source/hibernate").then().statusCode(200)
				.body(is("ack"));
	}

	@Test
	void knownNoHeader() {
		given().when().body(REQUEST_BODY).contentType(ContentType.JSON).post("api/jira/webhooks/source/hibernate")
				.then().statusCode(401);
	}

}
