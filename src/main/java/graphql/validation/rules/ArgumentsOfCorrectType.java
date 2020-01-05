package graphql.validation.rules;


import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.validation.*;

import java.util.HashMap;
import java.util.Map;

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
            Map<String, Object> extensions = new HashMap<>();
            extensions.put("argument", validationUtil.renderArgument());
            extensions.put("value", ValidationUtil.renderValue(validationUtil.getArgumentValue()));
            extensions.put("requiredType", ValidationUtil.renderType(validationUtil.getRequiredType()));
            extensions.put("objectType", ValidationUtil.renderType(validationUtil.getObjectType()));
            addError(ValidationErrorType.WrongType, argument.getSourceLocation(), validationUtil.getErrorMessage(), extensions);
        }
    }
}
