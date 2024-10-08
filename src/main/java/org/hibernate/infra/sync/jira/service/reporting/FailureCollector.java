package org.hibernate.infra.sync.jira.service.reporting;

// TODO: Do we want to include exception message/stacktrace etc in the reported error ?
//  I wonder if that wouldn't potentially expose some secret ...
public interface FailureCollector extends AutoCloseable {

	static FailureCollector collector(ReportingConfig config) {
		switch (config.type()) {
			case LOG -> {
				return LoggingFailureCollector.INSTANCE;
			}
			case THROW -> {
				return new ThrowingFailureCollector();
			}
			case GITHUB_ISSUE -> // TODO: add GH reporting:
				throw new UnsupportedOperationException("Github issue reporting is not yet supported");
			default ->
				throw new IllegalArgumentException("Unsupported failure collector type: %s".formatted(config.type()));
		}
	}

	void warning(String details);

	void warning(String details, Exception exception);

	void critical(String details);

	void critical(String details, Exception exception);

	@Override
	void close();
}
