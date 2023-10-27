package graphql.validation.rules;


import graphql.Internal;
import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.validation.AbstractRule;
import graphql.validation.ArgumentValidationUtil;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorCollector;

import java.util.Locale;

import static graphql.validation.ValidationErrorType.WrongType;

@Internal
public class ArgumentsOfCorrectType extends AbstractRule {

    public ArgumentsOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkArgument(Argument argument) {
        GraphQLArgument fieldArgument = getValidationContext().getArgument();
        if (fieldArgument == null) {
            return;
        }
        ArgumentValidationUtil validationUtil = new ArgumentValidationUtil(argument);
        if (!validationUtil.isValidLiteralValue(argument.getValue(), fieldArgument.getType(), getValidationContext().getSchema(), getValidationContext().getGraphQLContext(), Locale.getDefault())) {
            String message = i18n(WrongType, validationUtil.getMsgAndArgs());
            addError(ValidationError.newValidationError()
                    .validationErrorType(WrongType)
                    .sourceLocation(argument.getSourceLocation())
                    .description(message)
                    .extensions(validationUtil.getErrorExtensions()));
        }
    }
}
