package org.hibernate.infra.sync.jira;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "processing.events")
public interface ProcessingConfig {
	@WithDefault("1000")
	int queueSize();

	@WithDefault("20")
	int threads();
}
