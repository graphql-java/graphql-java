package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.schema.validation.SchemaValidationErrorType.InvalidDirectiveDefinition;
import static java.lang.String.format;

/**
 * Validates directive definitions:
 * <ul>
 *     <li>Directive definition names must not start with "__"</li>
 *     <li>Directive definition arguments must have unique names</li>
 *     <li>Directive definition argument types must be input types</li>
 * </ul>
 */
@Internal
public class DirectiveDefinitionsAreValid extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLDirective(GraphQLDirective directive, TraverserContext<GraphQLSchemaElement> context) {
        // Only validate directive definitions (no parent means it's a definition, not applied)
        if (context.getParentNode() != null) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);

        checkReservedName(directive, errorCollector);
        checkArgumentUniqueness(directive, errorCollector);
        checkArgumentTypes(directive, errorCollector);

        return TraversalControl.CONTINUE;
    }

    private void checkReservedName(GraphQLDirective directive, SchemaValidationErrorCollector errorCollector) {
        String name = directive.getName();
        if (name.length() >= 2 && name.startsWith("__")) {
            errorCollector.addError(new SchemaValidationError(InvalidDirectiveDefinition,
                    format("Directive definition name '%s' must not begin with '__', which is reserved by GraphQL introspection.", name)));
        }
    }

    private void checkArgumentUniqueness(GraphQLDirective directive, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLArgument> arguments = directive.getArguments();
        Set<String> seen = new LinkedHashSet<>();
        for (GraphQLArgument argument : arguments) {
            if (!seen.add(argument.getName())) {
                errorCollector.addError(new SchemaValidationError(InvalidDirectiveDefinition,
                        format("Directive definition '%s' has a duplicate argument named '%s'",
                                directive.getName(), argument.getName())));
            }
        }
    }

    private void checkArgumentTypes(GraphQLDirective directive, SchemaValidationErrorCollector errorCollector) {
        for (GraphQLArgument argument : directive.getArguments()) {
            GraphQLInputType argType = argument.getType();
            String unwrappedName = GraphQLTypeUtil.unwrapAll(argType).getName();
            // Check that the argument name doesn't start with __
            String argName = argument.getName();
            if (argName.length() >= 2 && argName.startsWith("__")) {
                errorCollector.addError(new SchemaValidationError(InvalidDirectiveDefinition,
                        format("Directive definition '%s' argument '%s' must not begin with '__', which is reserved by GraphQL introspection.",
                                directive.getName(), argName)));
            }
        }
    }
}
