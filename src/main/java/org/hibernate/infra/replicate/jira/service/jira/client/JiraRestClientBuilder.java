package org.hibernate.infra.replicate.jira.service.jira.client;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.hibernate.infra.replicate.jira.JiraConfig;

import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public class JiraRestClientBuilder {

	public static JiraRestClient of(JiraConfig.Instance jira) {
		JiraConfig.JiraUser jiraUser = jira.apiUser();

		Map<String, String> headers = jira.loginKind().headers(jiraUser.email(), jiraUser.token());

		QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder().baseUri(jira.apiUri())
				.connectTimeout(5, TimeUnit.MINUTES).readTimeout(10, TimeUnit.MINUTES)
				.clientHeadersFactory((incomingHeaders, clientOutgoingHeaders) -> {
					for (var entry : headers.entrySet()) {
						clientOutgoingHeaders.add(entry.getKey(), entry.getValue());
					}
					return clientOutgoingHeaders;
				});
		if (jira.logRequests()) {
			builder.clientLogger(CustomClientLogger.INSTANCE).loggingScope(LoggingScope.REQUEST_RESPONSE);
		}
		return builder.build(JiraRestClient.class);
	}

	private static class CustomClientLogger implements ClientLogger {
		private static final CustomClientLogger INSTANCE = new CustomClientLogger();

		@Override
		public void setBodySize(int bodySize) {
			// ignored
		}

		@Override
		public void logResponse(HttpClientResponse response, boolean redirect) {
			response.bodyHandler(body -> Log.infof("%s: %s %s, Status[%d %s], Headers[%s], Body:\n%s",
					redirect ? "Redirect" : "Response", response.request().getMethod(),
					response.request().absoluteURI(), response.statusCode(), response.statusMessage(),
					asString(response.headers()), bodyToString(body)));
		}

		@Override
		public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
			if (omitBody) {
				Log.infof("Request: %s %s Headers[%s], Body omitted", request.getMethod(), request.absoluteURI(),
						asString(request.headers()));
			} else if (body == null || body.length() == 0) {
				Log.infof("Request: %s %s Headers[%s], Empty body", request.getMethod(), request.absoluteURI(),
						asString(request.headers()));
			} else {
				Log.infof("Request: %s %s Headers[%s], Body:\n%s", request.getMethod(), request.absoluteURI(),
						asString(request.headers()), bodyToString(body));
			}
		}

		private String bodyToString(Buffer body) {
			if (body == null) {
				return "";
			} else {
				return body.toString();
			}
		}

		private String asString(MultiMap headers) {
			if (headers.isEmpty()) {
				return "";
			}
			StringBuilder sb = new StringBuilder();
			boolean isFirst = true;
			for (Map.Entry<String, String> entry : headers) {
				if (isFirst) {
					isFirst = false;
				} else {
					sb.append(' ');
				}
				sb.append(entry.getKey()).append('=').append(entry.getValue());
			}
			return headers.entries().stream()
					.map(entry -> "%s: %s".formatted(entry.getKey(), sanitize(entry.getKey(), entry.getValue())))
					.collect(Collectors.joining(" | "));
		}

		private String sanitize(String header, String value) {
			if ("Authorization".equalsIgnoreCase(header)) {
				return "***";
			} else {
				return value;
			}
		}
	}
}
