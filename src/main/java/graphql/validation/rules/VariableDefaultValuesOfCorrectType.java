package graphql.validation.rules;

import graphql.language.VariableDefinition;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLNonNull;
import graphql.validation.*;


/**
 * <p>VariableDefaultValuesOfCorrectType class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class VariableDefaultValuesOfCorrectType extends AbstractRule {


    /**
     * <p>Constructor for VariableDefaultValuesOfCorrectType.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public VariableDefaultValuesOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    /** {@inheritDoc} */
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
