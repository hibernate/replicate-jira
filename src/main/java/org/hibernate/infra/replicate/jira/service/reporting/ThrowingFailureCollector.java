package org.hibernate.infra.replicate.jira.service.reporting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ThrowingFailureCollector implements FailureCollector {

	private final Map<String, List<Failure>> failures = new HashMap<>();

	ThrowingFailureCollector() {
	}

	@Override
	public void warning(String details) {
		failures.computeIfAbsent("warning", k -> new ArrayList<>()).add(new Failure(details, null));
	}

	@Override
	public void warning(String details, Exception exception) {
		failures.computeIfAbsent("warning", k -> new ArrayList<>()).add(new Failure(details, exception));
	}

	@Override
	public void critical(String details) {
		failures.computeIfAbsent("critical", k -> new ArrayList<>()).add(new Failure(details, null));
	}

	@Override
	public void critical(String details, Exception exception) {
		failures.computeIfAbsent("critical", k -> new ArrayList<>()).add(new Failure(details, exception));
	}

	@Override
	public void close() {
		if (failures.isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder("Some problems occurred.");

		List<Exception> exceptions = new ArrayList<>();
		for (var entry : failures.entrySet()) {
			sb.append(entry.getValue()).append(":\n");
			for (Failure failure : entry.getValue()) {
				sb.append(failure.message()).append("\n");
				if (failure.cause() != null) {
					exceptions.add(failure.cause());
				}
			}
		}

		RuntimeException exception = new RuntimeException(sb.toString());
		exceptions.forEach(exception::addSuppressed);
		throw exception;
	}

	private record Failure(String message, Exception cause) {

	}
}
