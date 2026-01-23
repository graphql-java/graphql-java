package graphql.schema.transform;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.schema.SchemaTraverser;
import graphql.schema.transform.VisibleFieldPredicateEnvironment.VisibleFieldPredicateEnvironmentImpl;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        beforeTransformationHook.run();

        // Find root unused types BEFORE transformation
        // These are types that exist in the schema but are NOT reachable from operation types + directives
        Set<String> rootUnusedTypes = findRootUnusedTypes(schema);

        // we delete all fields that should be deleted
        // this assumes the field remove itself is semantically valid
        GraphQLSchema interimSchema = transformSchemaWithDeletes(schema,
                new FieldRemovalVisitor(visibleFieldPredicate));


        // cleanup schema
        // now we want to remove all types which are not reachable via root types, directives and the interface implements relationship
        SchemaTraverser schemaTraverser = new SchemaTraverser(childrenWithInterfaceImplementations(interimSchema));

        // first we observe all types we don't want to delete
        Set<String> observedTypes = new LinkedHashSet<>();
        TypeObservingVisitor typeObservingVisitor = new TypeObservingVisitor(observedTypes);
        schemaTraverser.depthFirst(typeObservingVisitor, getRootTypes(interimSchema));

        // Traverse from root unused types that still exist after transformation
        // This preserves originally unused types and their dependencies
        List<GraphQLSchemaElement> existingRootUnusedTypes = rootUnusedTypes.stream()
                .map(interimSchema::getType)
                .filter(Objects::nonNull)
                .map(type -> (GraphQLSchemaElement) type)
                .collect(Collectors.toList());

        if (!existingRootUnusedTypes.isEmpty()) {
            schemaTraverser.depthFirst(typeObservingVisitor, existingRootUnusedTypes);
        }

        // then we delete all the types which are not used anymore
        GraphQLSchema finalSchema = transformSchemaWithDeletes(interimSchema,
                new TypeRemovalVisitor(observedTypes));


        afterTransformationHook.run();

        return finalSchema;
    }

    /**
     * Finds root unused types - types that exist in additional types but are NOT reachable
     * from operation types (Query, Mutation, Subscription) and directives.
     */
    private Set<String> findRootUnusedTypes(GraphQLSchema schema) {
        // Collect all types reachable from operation roots + directives
        // Use a traverser that includes interface implementations
        Set<String> typesReachableFromRoots = new LinkedHashSet<>();
        SchemaTraverser traverser = new SchemaTraverser(childrenWithInterfaceImplementations(schema));
        TypeObservingVisitor visitor = new TypeObservingVisitor(typesReachableFromRoots);
        traverser.depthFirst(visitor, getRootTypes(schema));

        // Root unused types are additional types that are NOT reachable from roots
        Set<String> rootUnusedTypes = new LinkedHashSet<>();
        for (GraphQLNamedType type : schema.getAdditionalTypes()) {
            String typeName = type.getName();
            if (!typesReachableFromRoots.contains(typeName) && !isIntrospectionType(typeName)) {
                rootUnusedTypes.add(typeName);
            }
        }
        return rootUnusedTypes;
    }

    /**
     * Checks if a type is an introspection type that should be protected from removal.
     * This includes standard introspection types (starting with "__") and special types
     * like _AppliedDirective (starting with "_") added by IntrospectionWithDirectivesSupport.
     */
    private static boolean isIntrospectionType(String typeName) {
        return Introspection.isIntrospectionTypes(typeName) || typeName.startsWith("_");
    }

    /**
     * Creates a function that returns children of a schema element, including interface implementations.
     * This ensures that when traversing from an interface, we also visit all types that implement it.
     */
    private Function<GraphQLSchemaElement, List<GraphQLSchemaElement>> childrenWithInterfaceImplementations(GraphQLSchema schema) {

        return schemaElement -> {
            if (!(schemaElement instanceof GraphQLInterfaceType)) {
                return schemaElement.getChildren();
            }
            ArrayList<GraphQLSchemaElement> children = new ArrayList<>(schemaElement.getChildren());
            List<GraphQLObjectType> implementations = schema.getImplementations((GraphQLInterfaceType) schemaElement);
            children.addAll(implementations);
            return children;
        };
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
                node instanceof GraphQLUnionType ||
                node instanceof GraphQLScalarType) {
                observedTypes.add(((GraphQLNamedType) node).getName());
            }

            return TraversalControl.CONTINUE;
        }
    }

    private static class FieldRemovalVisitor extends GraphQLTypeVisitorStub {

        private final VisibleFieldPredicate visibilityPredicate;

        private final Set<GraphQLFieldDefinition> fieldDefinitionsToActuallyRemove = new LinkedHashSet<>();
        private final Set<GraphQLInputObjectField> inputObjectFieldsToDelete = new LinkedHashSet<>();

        private FieldRemovalVisitor(VisibleFieldPredicate visibilityPredicate) {
            this.visibilityPredicate = visibilityPredicate;
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

    private static class TypeRemovalVisitor extends GraphQLTypeVisitorStub {

        private final Set<String> protectedTypeNames;

        private TypeRemovalVisitor(Set<String> protectedTypeNames) {
            this.protectedTypeNames = protectedTypeNames;
        }


        @Override
        public TraversalControl visitGraphQLType(GraphQLSchemaElement node,
                                                 TraverserContext<GraphQLSchemaElement> context) {
            if (node instanceof GraphQLNamedType) {
                String name = ((GraphQLNamedType) node).getName();
                if (isIntrospectionType(name)) {
                    return TraversalControl.CONTINUE;
                }
            }
            if (node instanceof GraphQLObjectType ||
                node instanceof GraphQLEnumType ||
                node instanceof GraphQLInputObjectType ||
                node instanceof GraphQLInterfaceType ||
                node instanceof GraphQLUnionType ||
                node instanceof GraphQLScalarType) {
                String name = ((GraphQLNamedType) node).getName();
                if (!protectedTypeNames.contains(name)) {
                    return deleteNode(context);
                }
            }
            return TraversalControl.CONTINUE;
        }
    }


    private List<GraphQLSchemaElement> getRootTypes(GraphQLSchema schema) {
        return ImmutableList.<GraphQLSchemaElement>builder()
                .addAll(getOperationTypes(schema))
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
