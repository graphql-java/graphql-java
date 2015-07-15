package graphql.validation.rules;


import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.validation.*;

public class ArgumentsOfCorrectType extends AbstractRule {

    public ArgumentsOfCorrectType(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }

    @Override
    public void checkArgument(Argument argument) {
        GraphQLArgument fieldArgument = getValidationContext().getArgument();
        if (fieldArgument == null) return;
        if (!getValidationUtil().isValidLiteralValue(argument.getValue(), fieldArgument.getType())) {
            addError(new ValidationError(ValidationErrorType.WrongType));
        }
    }


}
