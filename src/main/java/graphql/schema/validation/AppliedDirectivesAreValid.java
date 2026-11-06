package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.List;
import java.util.Map;

import static graphql.schema.validation.SchemaValidationErrorType.InvalidAppliedDirective;
import static java.lang.String.format;

@Internal
public class AppliedDirectivesAreValid extends GraphQLTypeVisitorStub {

    @Override
    protected TraversalControl visitGraphQLType(GraphQLSchemaElement node, TraverserContext<GraphQLSchemaElement> context) {
        if (!(node instanceof GraphQLDirectiveContainer directiveContainer)) {
            return TraversalControl.CONTINUE;
        }
        Map<String, List<GraphQLDirective>> directivesByName = directiveContainer.getAllDirectivesByName();
        if (directivesByName.isEmpty()) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector collector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        for (Map.Entry<String, List<GraphQLDirective>> entry : directivesByName.entrySet()) {
            String directiveName = entry.getKey();
            GraphQLDirective directiveDef = schema.getDirective(directiveName);
            if (directiveDef != null) {
                checkNonRepeatable(collector, directiveContainer, directiveDef, entry.getValue());
            } else {
                addError(collector, format("A definition for directive '%s' could not be found", directiveName));
            }
        }

        return TraversalControl.CONTINUE;
    }

    private void checkNonRepeatable(SchemaValidationErrorCollector collector, GraphQLDirectiveContainer directiveContainer, GraphQLDirective directiveDef, List<GraphQLDirective> directives) {
        if (directiveDef.isNonRepeatable() && directives.size() > 1) {
            addNonRepeatableError(collector, directiveContainer, directiveDef.getName(), directives.size());
        }
    }

    private void addNonRepeatableError(SchemaValidationErrorCollector collector, GraphQLDirectiveContainer directiveContainer, String name, int howMany) {
        addError(collector, format("The directive '%s' on the '%s' called '%s' is a non repeatable directive but has been applied %d times",
                name,
                directiveContainer.getClass().getSimpleName(),
                directiveContainer.getName(),
                howMany));
    }

    private void addError(SchemaValidationErrorCollector collector, String message) {
        collector.addError(new SchemaValidationError(InvalidAppliedDirective, message));
    }

}
