package graphql.validation.rules;

import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.*;


public class KnownArgumentNames extends AbstractRule {

    public KnownArgumentNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    @Override
    public void checkArgument(Argument argument) {
        GraphQLDirective directive = getValidationContext().getDirective();
        if (directive != null) {
            GraphQLArgument directiveArgument = directive.getArgument(argument.getName());
            if (directiveArgument == null) {
                String message = String.format("Unknown argument %s on directive %s", argument.getName(), directive.getName());
                addError(new ValidationError(ValidationErrorType.UnknownArgument, argument.getSourceLocation(), message));
            }
            return;
        }
        GraphQLFieldDefinition fieldDef = getValidationContext().getFieldDef();
        if (fieldDef != null) {
            GraphQLArgument fieldArgument = fieldDef.getArgument(argument.getName());
            if (fieldArgument == null) {
                String message = String.format("Unknown argument %s onf field %s", argument.getName(), fieldDef.getName());
                addError(new ValidationError(ValidationErrorType.UnknownArgument, argument.getSourceLocation(), message));
            }

        }
    }
}
