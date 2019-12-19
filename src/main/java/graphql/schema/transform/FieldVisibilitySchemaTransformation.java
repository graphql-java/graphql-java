package graphql.schema.transform;

import static graphql.schema.SchemaTransformer.transformSchema;
import static graphql.util.TreeTransformerUtil.deleteNode;

import graphql.PublicApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.transform.VisibleFieldPredicateEnvironment.VisibleFieldPredicateEnvironmentImpl;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Transforms a schema by applying a visibility predicate to every field.
 */
@PublicApi
public class FieldVisibilitySchemaTransformation {

    private final VisibleFieldPredicate visibleFieldPredicate;

    public FieldVisibilitySchemaTransformation(VisibleFieldPredicate visibleFieldPredicate) {
        this.visibleFieldPredicate = visibleFieldPredicate;
    }

    /**
     * Before and after callbacks useful for side effects (logs, stopwatches etc).
     */
    protected void beforeTransformation() {}

    protected void afterTransformation() {}

    public final GraphQLSchema apply(GraphQLSchema schema) {
        Set<GraphQLType> observedTypes = new HashSet<>();
        Set<GraphQLType> removedTypes = new HashSet<>();
        final String queryTypeName = schema.getQueryType().getName();

        beforeTransformation();

        GraphQLSchema interimSchema = transformSchema(schema, new FieldVisibilityVisitor(visibleFieldPredicate,
                removedTypes, observedTypes));

        // remove types that are not used
        GraphQLSchema finalSchema = transformSchema(interimSchema,
                new TypeVisibilityVisitor(queryTypeName, observedTypes, removedTypes));

        afterTransformation();

        return finalSchema;
    }

    private static class FieldVisibilityVisitor extends GraphQLTypeVisitorStub {

        private final VisibleFieldPredicate visibilityPredicate;
        private final Set<GraphQLType> removedTypes;
        private final Set<GraphQLType> observedTypes;

        private FieldVisibilityVisitor(VisibleFieldPredicate visibilityPredicate,
                                       Set<GraphQLType> removedTypes, Set<GraphQLType> observedTypes) {
            this.visibilityPredicate = visibilityPredicate;
            this.removedTypes = removedTypes;
            this.observedTypes = observedTypes;
        }

        @Override
        public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node,
                                                          TraverserContext<GraphQLSchemaElement> context) {
            observedTypes.add(node);

            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType node,
                                                       TraverserContext<GraphQLSchemaElement> context) {

            if (context.getBreadcrumbs().stream()
                    .anyMatch(crumb -> crumb.getLocation().getName().equalsIgnoreCase("addTypes"))) {
                return TraversalControl.ABORT;
            }

            observedTypes.add(node);

            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition definition,
                                                            TraverserContext<GraphQLSchemaElement> context) {
            VisibleFieldPredicateEnvironment environment = new VisibleFieldPredicateEnvironmentImpl(
                    context.getParentNode());
            if (!visibilityPredicate.isVisible(definition, environment)) {
                deleteNode(context);
                removedTypes.add(definition.getType());
            }

            return TraversalControl.CONTINUE;
        }
    }

    private static class TypeVisibilityVisitor extends GraphQLTypeVisitorStub {

        private final String queryTypeName;
        private final Set<GraphQLType> observedTypes;
        private final Set<GraphQLType> removedTypes;

        private TypeVisibilityVisitor(String queryTypeName,
                                      Set<GraphQLType> observedTypes,
                                      Set<GraphQLType> removedTypes) {
            this.queryTypeName = queryTypeName;
            this.observedTypes = observedTypes;
            this.removedTypes = removedTypes;
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType node,
                                                       TraverserContext<GraphQLSchemaElement> context) {
            if (!observedTypes.contains(node) &&
                    node.getInterfaces().stream().anyMatch(removedTypes::contains) &&
                    !ScalarInfo.isStandardScalar(node.getName()) &&
                    !node.getName().equalsIgnoreCase(queryTypeName)) {
                return deleteNode(context);
            }

            return TraversalControl.CONTINUE;
        }
    }


}
