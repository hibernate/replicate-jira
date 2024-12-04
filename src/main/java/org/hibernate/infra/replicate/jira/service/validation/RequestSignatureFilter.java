package org.hibernate.infra.replicate.jira.service.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.hibernate.infra.replicate.jira.JiraConfig;

import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.WithFormRead;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RequestSignatureFilter {

	private static final Pattern PATH_UPSTREAM_WEBHOOK_PATTERN = Pattern.compile("/jira/webhooks/(.+)");
	private static final Pattern PATH_DOWNSTREAM_WEBHOOK_PATTERN = Pattern.compile("/jira/webhooks/mirror/(.+)");
	private static final BiPredicate<String, byte[]> ALLOW_ALL = (a, b) -> true;

	private final Map<String, BiPredicate<String, byte[]>> upstreamChecks;
	private final Map<String, BiPredicate<String, byte[]>> downstreamChecks;

	@Inject
	public RequestSignatureFilter(JiraConfig jiraConfig) {
		Map<String, BiPredicate<String, byte[]>> up = new HashMap<>();
		Map<String, BiPredicate<String, byte[]>> down = new HashMap<>();
		for (JiraConfig.JiraProjectGroup group : jiraConfig.projectGroup().values()) {
			for (var entry : group.projects().entrySet()) {
				up.put(entry.getKey(), check(entry.getValue().security()));
				down.put(entry.getKey(), check(entry.getValue().downstreamSecurity()));
			}
		}

		this.upstreamChecks = Collections.unmodifiableMap(up);
		this.downstreamChecks = Collections.unmodifiableMap(down);
	}

	private BiPredicate<String, byte[]> check(JiraConfig.WebHookSecurity security) {
		if (security.enabled()) {
			switch (security.type()) {
				case TOKEN -> {
					String secret = security.secret();
					return (header, body) -> secret.equals(header);
				}
				case SIGNATURE -> {
					Mac mac = fromSecret(security.secret());
					return (header, body) -> header.equals(signBytes(mac, body));
				}
				default -> throw new IllegalArgumentException("Unsupported security type: " + security.type());
			}
		} else {
			return null;
		}
	}

	@WithFormRead
	@ServerRequestFilter
	public Response checkSignature(ContainerRequestContext requestContext) throws IOException {
		String path = requestContext.getUriInfo().getPath();
		Matcher downstream = PATH_DOWNSTREAM_WEBHOOK_PATTERN.matcher(path);
		Matcher upstream = PATH_UPSTREAM_WEBHOOK_PATTERN.matcher(path);
		if ("POST".equals(requestContext.getMethod())) {
			BiPredicate<String, byte[]> check = null;
			if (downstream.matches()) {
				// for downstream automated actions we just send something in the header that we
				// compare here:3
				String project = downstream.group(1);
				check = downstreamChecks.get(project);
			} else if (upstream.matches()) {
				String project = upstream.group(1);
				check = upstreamChecks.get(project);
			}
			if (check != null) {
				String signature = requestContext.getHeaderString("x-hub-signature");

				if (signature == null || !requestContext.hasEntity()) {
					Log.warnf("Rejecting a web hook event because of the missing signature. Posted to %s", path);
					return Response.status(401).entity("Invalid request. Missing x-hub-signature header.").build();
				}

				if (check(requestContext, check, signature, path)) {
					return Response.status(401).entity("Signatures do not match.").build();
				}
			}
		}
		return null;
	}

	private static boolean check(ContainerRequestContext requestContext, BiPredicate<String, byte[]> check,
			String signature, String path) throws IOException {
		try (InputStream entityStream = requestContext.getEntityStream()) {
			byte[] payload = entityStream.readAllBytes();

			if (!check.test(signature, payload)) {
				Log.warnf("Rejecting a web hook event because of the signature mismatch. Posted to %s", path);
				return true;
			}
			requestContext.setEntityStream(new ByteArrayInputStream(payload));
		}
		return false;
	}

	public static String sign(String secret, String payload) {
		return signBytes(fromSecret(secret), payload.getBytes(StandardCharsets.UTF_8));
	}

	public static String signBytes(Mac mac, byte[] payload) {
		final byte[] digest = mac.doFinal(payload);
		final HexFormat hex = HexFormat.of();
		return "sha256=" + hex.formatHex(digest);
	}

	private static Mac fromSecret(String secret) {
		try {
			SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(keySpec);
			return mac;
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
	}
}
