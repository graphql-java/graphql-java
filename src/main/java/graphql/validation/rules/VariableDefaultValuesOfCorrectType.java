package graphql.validation.rules;

import graphql.Internal;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLInputType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.Locale;

import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.validation.ValidationErrorType.BadValueForDefaultArg;

@Internal
public class VariableDefaultValuesOfCorrectType extends AbstractRule {


    public VariableDefaultValuesOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        GraphQLInputType inputType = getValidationContext().getInputType();
        if (inputType == null) {
            return;
        }
        if (variableDefinition.getDefaultValue() != null
                && !getValidationUtil().isValidLiteralValue(variableDefinition.getDefaultValue(), inputType, getValidationContext().getSchema(), getValidationContext().getGraphQLContext(), getValidationContext().getI18n().getLocale())) {
            String message = i18n(BadValueForDefaultArg, "VariableDefaultValuesOfCorrectType.badDefault", variableDefinition.getDefaultValue(), simplePrint(inputType));
            addError(BadValueForDefaultArg, variableDefinition.getSourceLocation(), message);
        }
    }
}
