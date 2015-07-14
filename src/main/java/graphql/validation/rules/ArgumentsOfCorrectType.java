package graphql.validation.rules;


import graphql.language.Argument;
import graphql.schema.GraphQLFieldArgument;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationUtil;

public class ArgumentsOfCorrectType extends AbstractRule {

    private final ValidationContext validationContext;

    ValidationUtil validationUtil = new ValidationUtil();

    public ArgumentsOfCorrectType(ValidationContext validationContext) {
        this.validationContext = validationContext;
    }

    @Override
    public void checkArgument(Argument argument) {
        GraphQLFieldArgument fieldArgument = validationContext.getArgument();
        if (validationUtil.isValidLiteralValue(argument.getValue(), fieldArgument.getType())) {

        }
    }
}
