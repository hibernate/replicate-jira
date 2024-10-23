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

	private static final Pattern PATH_WEBHOOK_PATTERN = Pattern.compile("/jira/webhooks/(.+)");
	private final Map<String, Mac> macs;

	@Inject
	public RequestSignatureFilter(JiraConfig jiraConfig) {
		Map<String, Mac> macs = new HashMap<>();
		for (JiraConfig.JiraProjectGroup group : jiraConfig.projectGroup().values()) {
			for (var entry : group.projects().entrySet()) {
				JiraConfig.WebHookSecurity security = entry.getValue().security();
				if (security.enabled()) {
					macs.put(entry.getKey(), fromSecret(security.secret()));
				}
			}
		}

		this.macs = Collections.unmodifiableMap(macs);
	}

	@WithFormRead
	@ServerRequestFilter
	public Response checkSignature(ContainerRequestContext requestContext) throws IOException {
		String path = requestContext.getUriInfo().getPath();
		Matcher matcher = PATH_WEBHOOK_PATTERN.matcher(path);
		if ("POST".equals(requestContext.getMethod()) && matcher.matches()) {
			String project = matcher.group(1);
			Mac mac = macs.get(project);
			if (mac == null) {
				// means security is not enabled for this project...
				return null;
			}

			String signature = requestContext.getHeaderString("x-hub-signature");

			if (signature == null || !requestContext.hasEntity()) {
				Log.warnf("Rejecting a web hook event because of the missing signature. Posted to %s", path);
				return Response.status(401).entity("Invalid request. Missing x-hub-signature header.").build();
			}
			try (InputStream entityStream = requestContext.getEntityStream()) {
				byte[] payload = entityStream.readAllBytes();

				final String calculatedSignature = sign(mac, payload);
				if (!calculatedSignature.equals(signature)) {
					Log.warnf("Rejecting a web hook event because of the signature mismatch. Posted to %s", path);
					return Response.status(401).entity("Signatures do not match.").build();
				}
				requestContext.setEntityStream(new ByteArrayInputStream(payload));
			}
		}
		return null;
	}

	public static String sign(String secret, String payload) {
		return sign(fromSecret(secret), payload.getBytes(StandardCharsets.UTF_8));
	}

	public static String sign(Mac mac, byte[] payload) {
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
