package org.hibernate.infra.sync.jira.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;

/**
 * Since we do not depend on any resources let's just have this simplest check to see if the app is running.
 */
@Startup
@Readiness
@ApplicationScoped
public class IsAppRunningCheck implements HealthCheck {
	private static final String NAME = "Jira Sync Up";

	@Override
	public HealthCheckResponse call() {
		return HealthCheckResponse.builder()
				.name( NAME )
				.withData( "details", "The app is up and running, and should be able to accept webhook POSTs." )
				.up()
				.build();
	}

}
