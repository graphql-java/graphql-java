package graphql.validation.rules;


import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.validation.*;

public class ArgumentsOfCorrectType extends AbstractRule {

    public ArgumentsOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

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
