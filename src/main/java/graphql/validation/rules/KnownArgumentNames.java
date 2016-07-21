package graphql.validation.rules;

import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.Node;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class KnownArgumentNames extends AbstractRule {

    public KnownArgumentNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        GraphQLFieldDefinition fieldDef = getValidationContext().getFieldDef();
        if (fieldDef == null) return;
        checkForUnknownNames(fieldDef.getArguments(), field.getArguments());
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        GraphQLDirective graphQLDirective = getValidationContext().getDirective();
        if (graphQLDirective == null) return;
        checkForUnknownNames(graphQLDirective.getArguments(), directive.getArguments());
    }

    private void checkForUnknownNames(List<GraphQLArgument> argumentDefinitions, List<Argument> argumentValues) {
        Set<String> knownNames = argumentNameSet(argumentDefinitions);
        for (Argument argument : argumentValues) {
            if (!knownNames.contains(argument.getName())) {
                String message = String.format("Unknown argument %s", argument.getName());
                addError(new ValidationError(ValidationErrorType.UnknownArgument, argument.getSourceLocation(), message));
            }
        }
    }

    private Set<String> argumentNameSet(List<GraphQLArgument> argumentDefinitions) {
        Set<String> names = new HashSet<String>();
        for (GraphQLArgument argument : argumentDefinitions) {
            names.add(argument.getName());
        }
        return names;
    }

}
