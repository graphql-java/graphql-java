package graphql.execution.validation;

import graphql.PublicApi;

@PublicApi
public interface ValidationRule {

    ValidationResult validate(ValidationRuleEnvironment environment);
}
