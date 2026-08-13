package graphql.schema.transform;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNamedOutputType;
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
 * Transforms a schema by applying visibility predicates to fields and interface implementation relationships.
 * <p>
 * Field and interface implementation visibility are independent. Callers are responsible for ensuring that their
 * combined visibility decisions produce a valid schema. An invalid transformed schema causes schema construction to
 * fail with an {@link graphql.schema.validation.InvalidSchemaException}.
 */
@PublicApi
public class FieldVisibilitySchemaTransformation {

    private static final VisibleInterfaceImplementationPredicate ALL_INTERFACE_IMPLEMENTATIONS_VISIBLE = environment -> true;

    private final VisibleFieldPredicate visibleFieldPredicate;
    private final VisibleInterfaceImplementationPredicate visibleInterfaceImplementationPredicate;
    private final Runnable beforeTransformationHook;
    private final Runnable afterTransformationHook;

    public FieldVisibilitySchemaTransformation(VisibleFieldPredicate visibleFieldPredicate) {
        this(visibleFieldPredicate, ALL_INTERFACE_IMPLEMENTATIONS_VISIBLE, () -> {
        }, () -> {
        });
    }

    /**
     * Creates a schema transformation with independent visibility predicates for fields and interface implementation
     * relationships.
     *
     * @param visibleFieldPredicate                   controls field visibility
     * @param visibleInterfaceImplementationPredicate controls interface implementation relationship visibility
     */
    public FieldVisibilitySchemaTransformation(VisibleFieldPredicate visibleFieldPredicate,
                                               VisibleInterfaceImplementationPredicate visibleInterfaceImplementationPredicate) {
        this(visibleFieldPredicate, visibleInterfaceImplementationPredicate, () -> {
        }, () -> {
        });
    }

    public FieldVisibilitySchemaTransformation(VisibleFieldPredicate visibleFieldPredicate,
                                               Runnable beforeTransformationHook,
                                               Runnable afterTransformationHook) {
        this(visibleFieldPredicate, ALL_INTERFACE_IMPLEMENTATIONS_VISIBLE, beforeTransformationHook, afterTransformationHook);
    }

    /**
     * Creates a schema transformation with independent visibility predicates and lifecycle hooks.
     *
     * @param visibleFieldPredicate                   controls field visibility
     * @param visibleInterfaceImplementationPredicate controls interface implementation relationship visibility
     * @param beforeTransformationHook                runs before transformation
     * @param afterTransformationHook                 runs after transformation
     */
    public FieldVisibilitySchemaTransformation(VisibleFieldPredicate visibleFieldPredicate,
                                               VisibleInterfaceImplementationPredicate visibleInterfaceImplementationPredicate,
                                               Runnable beforeTransformationHook,
                                               Runnable afterTransformationHook) {
        this.visibleFieldPredicate = visibleFieldPredicate;
        this.visibleInterfaceImplementationPredicate = visibleInterfaceImplementationPredicate;
        this.beforeTransformationHook = beforeTransformationHook;
        this.afterTransformationHook = afterTransformationHook;
    }

    public final GraphQLSchema apply(GraphQLSchema schema) {

        beforeTransformationHook.run();

        // Find root unused types BEFORE transformation
        // These are types that exist in the schema but are NOT reachable from operation types + directives
        Set<String> rootUnusedTypes = findRootUnusedTypes(schema);

        // we delete all fields and interface implementation relationships that should be deleted
        // this assumes the combined removals are semantically valid
        GraphQLSchema interimSchema = transformSchemaWithDeletes(schema,
                new ElementRemovalVisitor(visibleFieldPredicate, visibleInterfaceImplementationPredicate));


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

    private static class ElementRemovalVisitor extends GraphQLTypeVisitorStub {

        private final VisibleFieldPredicate fieldVisibilityPredicate;
        private final VisibleInterfaceImplementationPredicate interfaceImplementationPredicate;

        private final Set<GraphQLFieldDefinition> fieldDefinitionsToActuallyRemove = new LinkedHashSet<>();
        private final Set<GraphQLInputObjectField> inputObjectFieldsToDelete = new LinkedHashSet<>();

        private ElementRemovalVisitor(VisibleFieldPredicate fieldVisibilityPredicate,
                                      VisibleInterfaceImplementationPredicate interfaceImplementationPredicate) {
            this.fieldVisibilityPredicate = fieldVisibilityPredicate;
            this.interfaceImplementationPredicate = interfaceImplementationPredicate;
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
            if (markInvisibleFields(objectType)) {
                return deleteNode(context);
            }
            List<GraphQLInterfaceType> visibleInterfaces = getVisibleInterfaces(objectType);
            if (visibleInterfaces.size() == objectType.getInterfaces().size()) {
                return TraversalControl.CONTINUE;
            }
            GraphQLObjectType changedObjectType = objectType.transform(builder -> builder.replaceInterfaces(visibleInterfaces));
            return changeNode(context, changedObjectType);
        }

        @Override
        public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType interfaceType, TraverserContext<GraphQLSchemaElement> context) {
            if (markInvisibleFields(interfaceType)) {
                return deleteNode(context);
            }
            List<GraphQLInterfaceType> visibleInterfaces = getVisibleInterfaces(interfaceType);
            if (visibleInterfaces.size() == interfaceType.getInterfaces().size()) {
                return TraversalControl.CONTINUE;
            }
            GraphQLInterfaceType changedInterfaceType = interfaceType.transform(builder -> builder.replaceInterfaces(visibleInterfaces));
            return changeNode(context, changedInterfaceType);
        }

        private boolean markInvisibleFields(GraphQLFieldsContainer fieldsContainer) {
            boolean allFieldsDeleted = true;
            for (GraphQLFieldDefinition fieldDefinition : fieldsContainer.getFieldDefinitions()) {
                VisibleFieldPredicateEnvironment environment = new VisibleFieldPredicateEnvironmentImpl(
                        fieldDefinition, fieldsContainer);
                if (!fieldVisibilityPredicate.isVisible(environment)) {
                    fieldDefinitionsToActuallyRemove.add(fieldDefinition);
                } else {
                    allFieldsDeleted = false;
                }
            }
            return allFieldsDeleted;
        }

        private List<GraphQLInterfaceType> getVisibleInterfaces(GraphQLImplementingType implementingType) {
            List<GraphQLInterfaceType> visibleInterfaces = new ArrayList<>();
            for (GraphQLNamedOutputType namedInterface : implementingType.getInterfaces()) {
                GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) namedInterface;
                VisibleInterfaceImplementationPredicateEnvironment environment =
                        new VisibleInterfaceImplementationPredicateEnvironmentImpl(implementingType, interfaceType);
                if (interfaceImplementationPredicate.isVisible(environment)) {
                    visibleInterfaces.add(interfaceType);
                }
            }
            return visibleInterfaces;
        }

        @Override
        public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType inputObjectType, TraverserContext<GraphQLSchemaElement> context) {
            boolean allFieldsDeleted = true;
            for (GraphQLInputObjectField inputField : inputObjectType.getFieldDefinitions()) {
                VisibleFieldPredicateEnvironment environment = new VisibleFieldPredicateEnvironmentImpl(
                        inputField, inputObjectType);
                if (!fieldVisibilityPredicate.isVisible(environment)) {
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
