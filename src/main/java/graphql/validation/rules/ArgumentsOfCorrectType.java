package graphql.validation.rules;


import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.validation.*;

/**
 * <p>ArgumentsOfCorrectType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ArgumentsOfCorrectType extends AbstractRule {

    /**
     * <p>Constructor for ArgumentsOfCorrectType.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public ArgumentsOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    /** {@inheritDoc} */
    @Override
    public void checkArgument(Argument argument) {
        GraphQLArgument fieldArgument = getValidationContext().getArgument();
        if (fieldArgument == null) return;
        if (!getValidationUtil().isValidLiteralValue(argument.getValue(), fieldArgument.getType())) {
            String message = String.format("argument value %s has wrong type", argument.getValue());
            addError(new ValidationError(ValidationErrorType.WrongType, argument.getSourceLocation(), message));
        }
    }


}
