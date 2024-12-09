package org.hibernate.infra.replicate.jira.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.hibernate.infra.replicate.jira.JiraConfig;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.mockito.Mockito;

class RequestSignatureFilterTest {

	@Test
	void checkSignature() throws Exception {
		RequestSignatureFilter filter = createFilter(true, "wrong-secret");

		assertThat(filter.checkSignature(requestContext("/path-not-matching-the-pattern"))).isNull();
		assertThat(filter.checkSignature(requestContext("/jira/webhooks/source/UNKNOWN"))).isNull();
		assertThat(filter.checkSignature(requestContext("/jira/webhooks/source/UNKNOWN", "POST", "smth"))).isNull();

		Response response = filter
				.checkSignature(requestContext("/jira/webhooks/source/PROJECT_GROUP_KEY", "POST", "smth"));
		assertThat(response).isNotNull();
		assertThat((String) response.getEntity()).contains("Missing x-hub-signature header");

		response = filter.checkSignature(requestContext("/jira/webhooks/source/PROJECT_GROUP_KEY", "POST", "smth",
				Map.of("x-hub-signature", "smth-wrong")));
		assertThat(response).isNotNull();
		assertThat((String) response.getEntity()).contains("Signatures do not match");

		ContainerRequestContext requestContext = requestContext("/jira/webhooks/source/PROJECT_GROUP_KEY", "POST",
				"smth",
				Map.of("x-hub-signature", "sha256=beb656dd03b5a81bee94aab4966943bf694929fcd359c45a1947277df2541a5d"));
		assertThat(filter.checkSignature(requestContext)).isNull();
		Mockito.verify(requestContext, Mockito.times(1)).setEntityStream(Mockito.any());
	}

	private static ContainerRequestContext requestContext(String path) {
		return requestContext(path, "GET", "");
	}

	private static ContainerRequestContext requestContext(String path, String method, String body) {
		return requestContext(path, method, body, Map.of());
	}

	private static ContainerRequestContext requestContext(String path, String method, String body,
			Map<String, String> headers) {
		ContainerRequestContext context = Mockito.mock(ContainerRequestContext.class);
		UriInfo uriInfo = Mockito.mock(UriInfo.class);
		Mockito.when(context.getUriInfo()).thenReturn(uriInfo);
		Mockito.when(uriInfo.getPath()).thenReturn(path);
		Mockito.when(context.getMethod()).thenReturn(method);
		Mockito.when(context.hasEntity()).thenReturn(!body.isEmpty());
		Mockito.when(context.getEntityStream())
				.thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		Mockito.when(context.getHeaderString(Mockito.anyString()))
				.thenAnswer(invocation -> headers.get(invocation.getArgument(0)));
		return context;
	}

	private static RequestSignatureFilter createFilter(boolean enabled, String secret)
			throws NoSuchAlgorithmException, InvalidKeyException {
		JiraConfig.JiraProjectGroup group = Mockito.mock(JiraConfig.JiraProjectGroup.class);
		JiraConfig.JiraProject project = Mockito.mock(JiraConfig.JiraProject.class);
		JiraConfig.WebHookSecurity value = new JiraConfig.WebHookSecurity() {
			@Override
			public boolean enabled() {
				return enabled;
			}

			@Override
			public String secret() {
				return secret;
			}

			@Override
			public Type type() {
				return Type.SIGNATURE;
			}
		};
		Mockito.when(group.security()).thenReturn(value);
		Mockito.when(project.downstreamSecurity()).thenReturn(value);

		Mockito.when(group.projects()).thenReturn(Map.of("PROJECT_KEY", project));

		return new RequestSignatureFilter(() -> Map.of("PROJECT_GROUP_KEY", group));
	}
}
