package org.hibernate.infra.replicate.jira.service.validation;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ConfiguredProjectValidator implements ConstraintValidator<ConfiguredProject, String> {

	@Inject
	Instance<ConfiguredProjectsService> configuredProjectsService;
	private boolean upstream;

	@Override
	public void initialize(ConfiguredProject constraintAnnotation) {
		upstream = constraintAnnotation.upstream();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		if (upstream
				? configuredProjectsService.get().isUpstreamProject(value)
				: configuredProjectsService.get().isDownstreamProject(value)) {
			return true;
		}

		context.unwrap(HibernateConstraintValidatorContext.class).addExpressionVariable("project", value);

		return false;
	}
}
