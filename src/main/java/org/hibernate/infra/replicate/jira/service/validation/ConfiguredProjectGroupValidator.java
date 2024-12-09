package org.hibernate.infra.replicate.jira.service.validation;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ConfiguredProjectGroupValidator implements ConstraintValidator<ConfiguredProjectGroup, String> {

	@Inject
	Instance<ConfiguredProjectsService> configuredProjectsService;

	@Override
	public void initialize(ConfiguredProjectGroup constraintAnnotation) {
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		if (configuredProjectsService.get().isProjectGroup(value)) {
			return true;
		}

		context.unwrap(HibernateConstraintValidatorContext.class).addExpressionVariable("projectGroup", value);

		return false;
	}
}
