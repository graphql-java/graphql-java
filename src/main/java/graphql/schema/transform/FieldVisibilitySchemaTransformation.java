package graphql.schema.transform;

import static graphql.schema.SchemaTransformer.transformSchema;
import static graphql.util.TreeTransformerUtil.deleteNode;

import graphql.PublicApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.transform.VisibleFieldPredicateEnvironment.VisibleFieldPredicateEnvironmentImpl;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

        // query, mutation, and subscription types should not be removed
        final Set<String> protectedTypeNames = new HashSet<>(Arrays.asList(
                schema.getQueryType(),
                schema.getSubscriptionType(),
                schema.getMutationType()
        )).stream()
                .filter(Objects::nonNull)
                .map(GraphQLObjectType::getName)
                .collect(Collectors.toSet());

        beforeTransformation();

        GraphQLSchema interimSchema = transformSchema(schema, new FieldVisibilityVisitor(visibleFieldPredicate,
                removedTypes, observedTypes));

        // remove types that are not used
        GraphQLSchema finalSchema = transformSchema(interimSchema,
                new TypeVisibilityVisitor(protectedTypeNames, observedTypes, removedTypes));

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
            if (context.getBreadcrumbs().stream()
                    .noneMatch(crumb -> crumb.getLocation().getName().equalsIgnoreCase("addTypes"))) {
                observedTypes.add(node);
            }

            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType node,
                                                       TraverserContext<GraphQLSchemaElement> context) {

            if (context.getBreadcrumbs().stream()
                    .noneMatch(crumb -> crumb.getLocation().getName().equalsIgnoreCase("addTypes"))) {
                observedTypes.add(node);
            }

            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition definition,
                                                            TraverserContext<GraphQLSchemaElement> context) {
            return visitField(definition, context);
        }

        @Override
        public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField definition,
                                                             TraverserContext<GraphQLSchemaElement> context) {
            return visitField(definition, context);
        }

        @Override
        public TraversalControl visitBackRef(TraverserContext<GraphQLSchemaElement> context) {
            if (context.thisNode() instanceof GraphQLInterfaceType || context.thisNode() instanceof GraphQLObjectType) {
                if (context.getBreadcrumbs().stream()
                        .noneMatch(crumb -> crumb.getLocation().getName().equalsIgnoreCase("addTypes"))) {
                    observedTypes.add((GraphQLType) context.thisNode());
                }
            }
            return TraversalControl.CONTINUE;
        }

        private TraversalControl visitField(GraphQLNamedSchemaElement element,
                                            TraverserContext<GraphQLSchemaElement> context) {

            VisibleFieldPredicateEnvironment environment = new VisibleFieldPredicateEnvironmentImpl(
                    element, context.getParentNode());
            if (!visibilityPredicate.isVisible(environment)) {
                deleteNode(context);

                if (element instanceof GraphQLFieldDefinition) {
                    removedTypes.add(((GraphQLFieldDefinition) element).getType());
                } else if (element instanceof GraphQLInputObjectField) {
                    removedTypes.add(((GraphQLInputObjectField) element).getType());
                }
            }

            return TraversalControl.CONTINUE;
        }
    }

    private static class TypeVisibilityVisitor extends GraphQLTypeVisitorStub {

        private final Set<String> protectedTypeNames;
        private final Set<GraphQLType> observedTypes;
        private final Set<GraphQLType> removedTypes;

        private TypeVisibilityVisitor(Set<String> protectedTypeNames,
                                      Set<GraphQLType> observedTypes,
                                      Set<GraphQLType> removedTypes) {
            this.protectedTypeNames = protectedTypeNames;
            this.observedTypes = observedTypes;
            this.removedTypes = removedTypes;
        }

        @Override
        public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node,
                                                          TraverserContext<GraphQLSchemaElement> context) {
            return super.visitGraphQLInterfaceType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType node,
                                                       TraverserContext<GraphQLSchemaElement> context) {
            if (!observedTypes.contains(node) &&
                    node.getInterfaces().stream().noneMatch(observedTypes::contains) &&
                    node.getInterfaces().stream().anyMatch(removedTypes::contains) &&
                    !ScalarInfo.isStandardScalar(node.getName()) &&
                    !protectedTypeNames.contains(node.getName())) {
                return deleteNode(context);
            }

            return TraversalControl.CONTINUE;
        }
    }


}
