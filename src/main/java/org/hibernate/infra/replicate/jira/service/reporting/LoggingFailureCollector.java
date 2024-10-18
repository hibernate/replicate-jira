package org.hibernate.infra.replicate.jira.service.reporting;

import io.quarkus.logging.Log;

class LoggingFailureCollector implements FailureCollector {

	static final FailureCollector INSTANCE = new LoggingFailureCollector();

	private LoggingFailureCollector() {
	}

	@Override
	public void warning(String details) {
		Log.warn(details);
	}

	@Override
	public void warning(String details, Exception exception) {
		Log.warnf(exception, details);
	}

	@Override
	public void critical(String details) {
		Log.error(details);
	}

	@Override
	public void critical(String details, Exception exception) {
		Log.errorf(exception, details);
	}

	@Override
	public void close() {
		// do nothing, we've already logged all
	}
}
