package org.hibernate.infra.replicate.jira;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "processing.events")
public interface ProcessingConfig {
	@WithDefault("10000")
	int queueSize();

	@WithDefault("2")
	int threads();
}
