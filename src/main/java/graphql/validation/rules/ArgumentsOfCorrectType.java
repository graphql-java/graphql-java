package graphql.validation.rules;


import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationError;

public class ArgumentsOfCorrectType extends AbstractRule {


    @Override
    public void checkArgument(Argument argument) {
        GraphQLArgument fieldArgument = getValidationContext().getArgument();
        if (fieldArgument == null) return;
        if (!getValidationUtil().isValidLiteralValue(argument.getValue(), fieldArgument.getType())) {
            getValidationContext().addError(new ValidationError("Wrong type"));
        }
    }
}
