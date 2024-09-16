package org.hibernate.infra.sync.jira.service.reporting;


// TODO: Do we want to include exception message/stacktrace etc in the reported error ?
//  I wonder if that wouldn't potentially expose some secret ...
public interface FailureCollector extends AutoCloseable {

	static FailureCollector collector(ReportingConfig config) {
		// TODO: add GH reporting:
		return LoggingFailureCollector.INSTANCE;
	}

	void warning(String details);

	void warning(String details, Exception exception);

	void critical(String details);

	void critical(String details, Exception exception);

	@Override
	void close();
}
