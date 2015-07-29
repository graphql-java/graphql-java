package graphql.validation.rules;

import graphql.language.VariableDefinition;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLNonNull;
import graphql.validation.*;


public class VariableDefaultValuesOfCorrectType extends AbstractRule {


    public VariableDefaultValuesOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        GraphQLInputType inputType = getValidationContext().getInputType();
        if (inputType == null) return;
        if (inputType instanceof GraphQLNonNull && variableDefinition.getDefaultValue() != null) {
            String message = "Missing value for non null type";
            addError(new ValidationError(ValidationErrorType.DefaultForNonNullArgument, variableDefinition.getSourceLocation(), message));
        }
        if (variableDefinition.getDefaultValue() != null
                && !getValidationUtil().isValidLiteralValue(variableDefinition.getDefaultValue(), inputType)) {
            String message = String.format("Bad default value %s for type %s", variableDefinition.getDefaultValue(), inputType.getName());
            addError(new ValidationError(ValidationErrorType.BadValueForDefaultArg, variableDefinition.getSourceLocation(), message));
        }
    }
}
