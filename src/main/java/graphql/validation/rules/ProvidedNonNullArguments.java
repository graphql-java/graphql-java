package graphql.validation.rules;


import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.validation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProvidedNonNullArguments extends AbstractRule {

    public ProvidedNonNullArguments(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }

    @Override
    public void checkField(Field field) {
        GraphQLFieldDefinition fieldDef = getValidationContext().getFieldDef();
        if (fieldDef == null) return;
        Map<String, Argument> argumentMap = argumentMap(field.getArguments());

        for (GraphQLArgument graphQLArgument : fieldDef.getArguments()) {
            Argument argument = argumentMap.get(graphQLArgument.getName());
            if (argument == null
                    && (graphQLArgument.getType() instanceof GraphQLNonNull)) {
                addError(new ValidationError(ValidationErrorType.MissingFieldArgument));
            }
        }
    }

    private Map<String, Argument> argumentMap(List<Argument> arguments) {
        Map<String, Argument> result = new LinkedHashMap<>();
        for (Argument argument : arguments) {
            result.put(argument.getName(), argument);
        }
        return result;
    }


    @Override
    public void checkDirective(Directive directive) {
        GraphQLDirective graphQLDirective = getValidationContext().getDirective();
        if (graphQLDirective == null) return;
        Map<String, Argument> argumentMap = argumentMap(directive.getArguments());

        for (GraphQLArgument graphQLArgument : graphQLDirective.getArguments()) {
            Argument argument = argumentMap.get(graphQLArgument.getName());
            if (argument == null
                    && (graphQLArgument.getType() instanceof GraphQLNonNull)) {
                addError(new ValidationError(ValidationErrorType.MissingDirectiveArgument));
            }
        }
    }
}
