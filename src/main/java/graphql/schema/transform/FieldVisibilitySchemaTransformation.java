package graphql.schema.transform;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.schema.SchemaTraverser;
import graphql.schema.impl.SchemaUtil;
import graphql.schema.transform.VisibleFieldPredicateEnvironment.VisibleFieldPredicateEnvironmentImpl;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static graphql.schema.SchemaTransformer.transformSchema;
import static graphql.schema.SchemaTransformer.transformSchemaWithDeletes;

/**
 * Transforms a schema by applying a visibility predicate to every field.
 */
@PublicApi
public class FieldVisibilitySchemaTransformation {

    private final VisibleFieldPredicate visibleFieldPredicate;
    private final Runnable beforeTransformationHook;
    private final Runnable afterTransformationHook;

    public FieldVisibilitySchemaTransformation(VisibleFieldPredicate visibleFieldPredicate) {
        this(visibleFieldPredicate, () -> {
        }, () -> {
        });
    }

    public FieldVisibilitySchemaTransformation(VisibleFieldPredicate visibleFieldPredicate,
                                               Runnable beforeTransformationHook,
                                               Runnable afterTransformationHook) {
        this.visibleFieldPredicate = visibleFieldPredicate;
        this.beforeTransformationHook = beforeTransformationHook;
        this.afterTransformationHook = afterTransformationHook;
    }

    public final GraphQLSchema apply(GraphQLSchema schema) {
        Set<String> observedBeforeTransform = new LinkedHashSet<>();
        Set<String> observedAfterTransform = new LinkedHashSet<>();
        Set<GraphQLType> markedForRemovalTypes = new HashSet<>();

        // query, mutation, and subscription types should not be removed
        final Set<String> protectedTypeNames = new HashSet<>();
        for (GraphQLObjectType graphQLObjectType : getOperationTypes(schema)) {
            protectedTypeNames.add(graphQLObjectType.getName());
        }

        beforeTransformationHook.run();

        new SchemaTraverser(getChildrenFn(schema)).depthFirst(new TypeObservingVisitor(observedBeforeTransform), getRootTypes(schema));

        // remove fields
        GraphQLSchema interimSchema = transformSchemaWithDeletes(schema,
                new FieldRemovalVisitor(visibleFieldPredicate, markedForRemovalTypes));

        new SchemaTraverser(getChildrenFn(interimSchema)).depthFirst(new TypeObservingVisitor(observedAfterTransform), getRootTypes(interimSchema));

        // remove types that are not used after removing fields - (connected schema only)
        GraphQLSchema connectedSchema = transformSchema(interimSchema,
                new TypeVisibilityVisitor(protectedTypeNames, observedBeforeTransform, observedAfterTransform));

        // ensure markedForRemovalTypes are not referenced by other schema elements, and delete from the schema
        // the ones that aren't.
        GraphQLSchema finalSchema = removeUnreferencedTypes(markedForRemovalTypes, connectedSchema);

        afterTransformationHook.run();

        return finalSchema;
    }

    // Creates a getChildrenFn that includes interface
    private Function<GraphQLSchemaElement, List<GraphQLSchemaElement>> getChildrenFn(GraphQLSchema schema) {
        Map<String, List<GraphQLImplementingType>> interfaceImplementations = new SchemaUtil().groupImplementationsForInterfacesAndObjects(schema);

        return graphQLSchemaElement -> {
            if (!(graphQLSchemaElement instanceof GraphQLInterfaceType)) {
                return graphQLSchemaElement.getChildren();
            }
            ArrayList<GraphQLSchemaElement> children = new ArrayList<>(graphQLSchemaElement.getChildren());
            List<GraphQLImplementingType> implementations = interfaceImplementations.get(((GraphQLInterfaceType) graphQLSchemaElement).getName());
            if (implementations != null) {
                children.addAll(implementations);
            }
            return children;
        };
    }

    private GraphQLSchema removeUnreferencedTypes(Set<GraphQLType> markedForRemovalTypes, GraphQLSchema connectedSchema) {
        GraphQLSchema withoutAdditionalTypes = connectedSchema.transform(builder -> {
            Set<GraphQLNamedType> additionalTypes = new HashSet<>(connectedSchema.getAdditionalTypes());
            additionalTypes.removeAll(markedForRemovalTypes);
            builder.clearAdditionalTypes();
            builder.additionalTypes(additionalTypes);
        });

        // remove from markedForRemovalTypes any type that might still be referenced by other schema elements
        transformSchema(withoutAdditionalTypes, new AdditionalTypeVisibilityVisitor(markedForRemovalTypes));

        // finally remove the types on the schema we are certain aren't referenced by any other node.
        return transformSchema(connectedSchema, new GraphQLTypeVisitorStub() {
            @Override
            protected TraversalControl visitGraphQLType(GraphQLSchemaElement node, TraverserContext<GraphQLSchemaElement> context) {
                if (node instanceof GraphQLType && markedForRemovalTypes.contains(node)) {
                    return deleteNode(context);
                }
                return super.visitGraphQLType(node, context);
            }
        });
    }

    private static class TypeObservingVisitor extends GraphQLTypeVisitorStub {

        private final Set<String> observedTypes;


        private TypeObservingVisitor(Set<String> observedTypes) {
            this.observedTypes = observedTypes;
        }

        @Override
        protected TraversalControl visitGraphQLType(GraphQLSchemaElement node,
                                                    TraverserContext<GraphQLSchemaElement> context) {
            if (node instanceof GraphQLObjectType ||
                node instanceof GraphQLEnumType ||
                node instanceof GraphQLInputObjectType ||
                node instanceof GraphQLInterfaceType ||
                node instanceof GraphQLUnionType) {
                observedTypes.add(((GraphQLNamedType) node).getName());
            }

            return TraversalControl.CONTINUE;
        }
    }

    private static class FieldRemovalVisitor extends GraphQLTypeVisitorStub {

        private final VisibleFieldPredicate visibilityPredicate;
        private final Set<GraphQLType> removedTypes;

        private final Set<GraphQLFieldDefinition> fieldDefinitionsToActuallyRemove = new LinkedHashSet<>();
        private final Set<GraphQLInputObjectField> inputObjectFieldsToDelete = new LinkedHashSet<>();

        private FieldRemovalVisitor(VisibleFieldPredicate visibilityPredicate,
                                    Set<GraphQLType> removedTypes) {
            this.visibilityPredicate = visibilityPredicate;
            this.removedTypes = removedTypes;
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
            return visitFieldsContainer(objectType, context);
        }

        @Override
        public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType objectType, TraverserContext<GraphQLSchemaElement> context) {
            return visitFieldsContainer(objectType, context);
        }

        private TraversalControl visitFieldsContainer(GraphQLFieldsContainer fieldsContainer, TraverserContext<GraphQLSchemaElement> context) {
            boolean allFieldsDeleted = true;
            for (GraphQLFieldDefinition fieldDefinition : fieldsContainer.getFieldDefinitions()) {
                VisibleFieldPredicateEnvironment environment = new VisibleFieldPredicateEnvironmentImpl(
                        fieldDefinition, fieldsContainer);
                if (!visibilityPredicate.isVisible(environment)) {
                    fieldDefinitionsToActuallyRemove.add(fieldDefinition);
                    removedTypes.add(fieldDefinition.getType());
                } else {
                    allFieldsDeleted = false;
                }
            }
            if (allFieldsDeleted) {
                // we are deleting the whole interface type because all fields are supposed to be deleted
                return deleteNode(context);
            } else {
                return TraversalControl.CONTINUE;
            }
        }

        @Override
        public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType inputObjectType, TraverserContext<GraphQLSchemaElement> context) {
            boolean allFieldsDeleted = true;
            for (GraphQLInputObjectField inputField : inputObjectType.getFieldDefinitions()) {
                VisibleFieldPredicateEnvironment environment = new VisibleFieldPredicateEnvironmentImpl(
                        inputField, inputObjectType);
                if (!visibilityPredicate.isVisible(environment)) {
                    inputObjectFieldsToDelete.add(inputField);
                    removedTypes.add(inputField.getType());
                } else {
                    allFieldsDeleted = false;
                }
            }
            if (allFieldsDeleted) {
                // we are deleting the whole input object type because all fields are supposed to be deleted
                return deleteNode(context);
            } else {
                return TraversalControl.CONTINUE;
            }

        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition definition,
                                                            TraverserContext<GraphQLSchemaElement> context) {
            if (fieldDefinitionsToActuallyRemove.contains(definition)) {
                return deleteNode(context);
            } else {
                return TraversalControl.CONTINUE;
            }
        }

        @Override
        public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField definition,
                                                             TraverserContext<GraphQLSchemaElement> context) {
            if (inputObjectFieldsToDelete.contains(definition)) {
                return deleteNode(context);
            } else {
                return TraversalControl.CONTINUE;
            }
        }
    }

    private static class TypeVisibilityVisitor extends GraphQLTypeVisitorStub {

        private final Set<String> protectedTypeNames;
        private final Set<String> observedBeforeTransform;
        private final Set<String> observedAfterTransform;

        private TypeVisibilityVisitor(Set<String> protectedTypeNames,
                                      Set<String> observedTypes,
                                      Set<String> observedAfterTransform) {
            this.protectedTypeNames = protectedTypeNames;
            this.observedBeforeTransform = observedTypes;
            this.observedAfterTransform = observedAfterTransform;
        }

        @Override
        public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node,
                                                          TraverserContext<GraphQLSchemaElement> context) {
            return super.visitGraphQLInterfaceType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLType(GraphQLSchemaElement node,
                                                 TraverserContext<GraphQLSchemaElement> context) {
            if (node instanceof GraphQLObjectType ||
                node instanceof GraphQLEnumType ||
                node instanceof GraphQLInputObjectType ||
                node instanceof GraphQLInterfaceType ||
                node instanceof GraphQLUnionType) {
                String name = ((GraphQLNamedType) node).getName();
                if (observedBeforeTransform.contains(name) &&
                    !observedAfterTransform.contains(name)
                    && !protectedTypeNames.contains(name)
                ) {
                    return deleteNode(context);
                }
            }
            return TraversalControl.CONTINUE;
        }
    }

    private static class AdditionalTypeVisibilityVisitor extends GraphQLTypeVisitorStub {

        private final Set<GraphQLType> markedForRemovalTypes;

        private AdditionalTypeVisibilityVisitor(Set<GraphQLType> markedForRemovalTypes) {
            this.markedForRemovalTypes = markedForRemovalTypes;
        }

        @Override
        public TraversalControl visitGraphQLType(GraphQLSchemaElement node,
                                                 TraverserContext<GraphQLSchemaElement> context) {

            if (node instanceof GraphQLNamedType) {
                GraphQLNamedType namedType = (GraphQLNamedType) node;
                // we encountered a node referencing one of the marked types, so it should not be removed.
                if (markedForRemovalTypes.contains(node)) {
                    markedForRemovalTypes.remove(namedType);
                }
            }

            return TraversalControl.CONTINUE;
        }
    }

    private List<GraphQLSchemaElement> getRootTypes(GraphQLSchema schema) {
        return ImmutableList.<GraphQLSchemaElement>builder()
                .addAll(getOperationTypes(schema))
                // Include directive definitions as roots, since they won't be removed in the filtering process.
                // Some types (enums, input types, etc.) might be reachable only by directive definitions (and
                // not by other types or fields).
                .addAll(schema.getDirectives())
                .build();
    }

    private List<GraphQLObjectType> getOperationTypes(GraphQLSchema schema) {
        return Stream.of(
                schema.getQueryType(),
                schema.getSubscriptionType(),
                schema.getMutationType()
        ).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
