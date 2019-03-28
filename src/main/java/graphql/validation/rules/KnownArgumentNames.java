package graphql.validation.rules;

import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.UnknownArgument;
import static graphql.validation.ValidationErrorType.UnknownDirective;


public class KnownArgumentNames extends AbstractRule {

    public KnownArgumentNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    @Override
    public void checkArgument(Argument argument) {
        GraphQLDirective directiveDef = getValidationContext().getDirective();
        if (directiveDef != null) {
            GraphQLArgument directiveArgument = directiveDef.getArgument(argument.getName());
            if (directiveArgument == null) {
                String message = i18n(UnknownDirective, "KnownArgumentNames.unknownDirectiveArg", argument.getName());
                addError(UnknownDirective, argument.getSourceLocation(), message);
            }

            return;
        }

        GraphQLFieldDefinition fieldDef = getValidationContext().getFieldDef();
        if (fieldDef == null) return;
        GraphQLArgument fieldArgument = fieldDef.getArgument(argument.getName());
        if (fieldArgument == null) {
            String message = i18n(UnknownArgument, "KnownArgumentNames.unknownFieldArg", argument.getName());

            addError(UnknownArgument, argument.getSourceLocation(), message);
        }
    }
}
