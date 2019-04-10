package graphql.validation.rules;

import graphql.language.VariableDefinition;
import graphql.schema.GraphQLInputType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.validation.ValidationErrorType.BadValueForDefaultArg;
import static graphql.validation.ValidationErrorType.DefaultForNonNullArgument;


public class VariableDefaultValuesOfCorrectType extends AbstractRule {


    public VariableDefaultValuesOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        GraphQLInputType inputType = getValidationContext().getInputType();
        if (inputType == null) return;
        if (isNonNull(inputType) && variableDefinition.getDefaultValue() != null) {
            String message = i18n(DefaultForNonNullArgument, "VariableDefaultValuesOfCorrectType.missingValue");
            addError(DefaultForNonNullArgument, variableDefinition.getSourceLocation(), message);
        }
        if (variableDefinition.getDefaultValue() != null
                && !getValidationUtil().isValidLiteralValue(variableDefinition.getDefaultValue(), inputType, getValidationContext().getSchema())) {
            String message = i18n(BadValueForDefaultArg, "VariableDefaultValuesOfCorrectType.badDefault",
                    variableDefinition.getDefaultValue(), inputType.getName());
            addError(BadValueForDefaultArg, variableDefinition.getSourceLocation(), message);
        }
    }
}
