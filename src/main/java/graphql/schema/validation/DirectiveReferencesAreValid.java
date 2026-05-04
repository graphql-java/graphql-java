package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static java.lang.String.format;

@Internal
public class DirectiveReferencesAreValid extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLDirective(GraphQLDirective directive, TraverserContext<GraphQLSchemaElement> context) {
        if (context.getParentNode() != null) {
            return TraversalControl.CONTINUE;
        }

        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        DirectiveCycleDetector detector = new DirectiveCycleDetector(schema);
        String cycle = detector.findCycle(directive);

        if (cycle == null) {
            return TraversalControl.CONTINUE;
        }

        String message = format("The directive cycle '%s' is invalid", cycle);
        errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidDirectiveDefinition, message));
        return TraversalControl.CONTINUE;
    }
}
