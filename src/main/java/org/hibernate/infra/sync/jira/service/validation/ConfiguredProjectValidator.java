package org.hibernate.infra.sync.jira.service.validation;

import java.util.Locale;
import java.util.Set;

import org.hibernate.infra.sync.jira.JiraConfig;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// @ApplicationScoped
public class ConfiguredProjectValidator implements ConstraintValidator<ConfiguredProject, String> {

	private final Set<String> projects;

	//@Inject
	public ConfiguredProjectValidator() {
		JiraConfig jiraConfig = null;
		projects = Set.of();
		// jiraConfig.projects().keySet().stream().map( s -> s.toLowerCase( Locale.ROOT ) ).collect( Collectors.toSet() );
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if ( value == null ) {
			return true;
		}
		if ( projects.contains( value.toLowerCase( Locale.ROOT ) ) ) {
			return true;
		}

		context.unwrap( HibernateConstraintValidatorContext.class ).addExpressionVariable( "project", value );

		return false;
	}
}
