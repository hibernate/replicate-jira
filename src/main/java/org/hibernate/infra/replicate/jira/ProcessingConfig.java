package org.hibernate.infra.replicate.jira;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "processing.events")
public interface ProcessingConfig {
	/**
	 * Define how many events can be acknowledged and put on the pending queue before
	 * acknowledging an event results in blocking the response and waiting for the queue to free some space.
	 */
	@WithDefault("10000")
	int queueSize();

	/**
	 * Define the number of threads to use when processing queued events.
	 * <p>
	 * Note, having a lot of processing threads might not bring much benefit as processing
	 * may also be limited by {@link JiraConfig.EventProcessing}
	 */
	@WithDefault("2")
	int threads();
}
