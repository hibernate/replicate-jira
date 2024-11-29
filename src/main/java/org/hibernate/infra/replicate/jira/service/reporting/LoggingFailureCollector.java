package org.hibernate.infra.replicate.jira.service.reporting;

import io.quarkus.logging.Log;

class LoggingFailureCollector implements FailureCollector {

	static final FailureCollector INSTANCE = new LoggingFailureCollector();

	private LoggingFailureCollector() {
	}

	@Override
	public void warning(String details) {
		Log.warn(escape(details));
	}

	@Override
	public void warning(String details, Exception exception) {
		Log.warnf(exception, escape(details));
	}

	@Override
	public void critical(String details) {
		Log.error(escape(details));
	}

	@Override
	public void critical(String details, Exception exception) {
		Log.errorf(exception, escape(details));
	}

	@Override
	public void close() {
		// do nothing, we've already logged all
	}

	private String escape(String s) {
		return s.replace('{', '[').replace('}', ']');
	}
}
