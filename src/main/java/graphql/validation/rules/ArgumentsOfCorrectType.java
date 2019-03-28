package graphql.validation.rules;


import graphql.i18n.I18nMsg;
import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.validation.AbstractRule;
import graphql.validation.ArgumentValidationUtil;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.WrongType;

public class ArgumentsOfCorrectType extends AbstractRule {

    public ArgumentsOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkArgument(Argument argument) {
        GraphQLArgument fieldArgument = getValidationContext().getArgument();
        if (fieldArgument == null) return;
        ArgumentValidationUtil validationUtil = new ArgumentValidationUtil(argument);
        if (!validationUtil.isValidLiteralValue(argument.getValue(), fieldArgument.getType(), getValidationContext().getSchema())) {
            String formattedMsg = i18n(WrongType, validationUtil.getMsgAndArgs());
            addError(WrongType, argument.getSourceLocation(), formattedMsg);
        }
    }
}
