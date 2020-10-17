package graphql.validation.rules;

import graphql.Internal;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLInputType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import static graphql.schema.GraphQLTypeUtil.simplePrint;


@Internal
public class VariableDefaultValuesOfCorrectType extends AbstractRule {


    public VariableDefaultValuesOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        GraphQLInputType inputType = getValidationContext().getInputType();
        if (inputType == null) return;
        if (variableDefinition.getDefaultValue() != null
                && !getValidationUtil().isValidLiteralValue(variableDefinition.getDefaultValue(), inputType, getValidationContext().getSchema())) {
            String message = String.format("Bad default value %s for type %s", variableDefinition.getDefaultValue(), simplePrint(inputType));
            addError(ValidationErrorType.BadValueForDefaultArg, variableDefinition.getSourceLocation(), message);
        }
    }
}
