package graphql.schema.transform;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.introspection.Introspection;
import org.jspecify.annotations.NullMarked;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
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
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
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
@NullMarked
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

        DeletionPlan deletionPlan = computeDeletionPlan(schema);

        // we delete all fields and interface implementation relationships that should be deleted
        // this assumes the combined removals are semantically valid
        GraphQLSchema interimSchema = transformSchemaWithDeletes(schema,
                new ElementRemovalVisitor(deletionPlan, visibleInterfaceImplementationPredicate));


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

    private DeletionPlan computeDeletionPlan(GraphQLSchema schema) {
        DeletionPlan plan = new DeletionPlan();
        List<GraphQLNamedType> allTypes = schema.getAllTypesAsList();
        for (GraphQLNamedType type : allTypes) {
            evaluateInitialVisibility(type, plan);
        }
        propagateEmptyTypeDeletions(schema, allTypes, plan);
        return plan;
    }

    private void evaluateInitialVisibility(GraphQLNamedType type, DeletionPlan plan) {
        if (type instanceof GraphQLFieldsContainer) {
            evaluateFieldsContainerVisibility((GraphQLFieldsContainer) type, plan);
            return;
        }
        if (type instanceof GraphQLInputObjectType) {
            evaluateInputObjectVisibility((GraphQLInputObjectType) type, plan);
        }
    }

    private void evaluateFieldsContainerVisibility(GraphQLFieldsContainer fieldsContainer, DeletionPlan plan) {
        for (GraphQLFieldDefinition fieldDefinition : fieldsContainer.getFieldDefinitions()) {
            VisibleFieldPredicateEnvironment environment = new VisibleFieldPredicateEnvironmentImpl(
                    fieldDefinition, fieldsContainer);
            if (!visibleFieldPredicate.isVisible(environment)) {
                plan.fieldsToDelete.add(fieldDefinition);
            }
        }
    }

    private void evaluateInputObjectVisibility(GraphQLInputObjectType inputObjectType, DeletionPlan plan) {
        for (GraphQLInputObjectField inputField : inputObjectType.getFieldDefinitions()) {
            VisibleFieldPredicateEnvironment environment = new VisibleFieldPredicateEnvironmentImpl(
                    inputField, inputObjectType);
            if (!visibleFieldPredicate.isVisible(environment)) {
                plan.inputFieldsToDelete.add(inputField);
            }
        }
    }

    private void propagateEmptyTypeDeletions(GraphQLSchema schema, List<GraphQLNamedType> allTypes, DeletionPlan plan) {
        GraphQLObjectType queryType = schema.getQueryType();
        boolean changed = true;
        while (changed) {
            changed = markNewlyEmptiedTypes(allTypes, queryType, plan);
            if (!plan.typesToDelete.isEmpty() && markElementsReferencingDeletedTypes(allTypes, plan)) {
                changed = true;
            }
        }
    }

    private boolean markNewlyEmptiedTypes(List<GraphQLNamedType> allTypes,
                                          GraphQLObjectType queryType,
                                          DeletionPlan plan) {
        boolean changed = false;
        for (GraphQLNamedType type : allTypes) {
            if (type == queryType || plan.typesToDelete.contains(type.getName())) {
                continue;
            }
            if (isTypeEmptied(type, plan)) {
                plan.typesToDelete.add(type.getName());
                changed = true;
            }
        }
        return changed;
    }

    private boolean isTypeEmptied(GraphQLNamedType type, DeletionPlan plan) {
        if (type instanceof GraphQLFieldsContainer) {
            return areAllFieldsDeleted((GraphQLFieldsContainer) type, plan.fieldsToDelete);
        }
        if (type instanceof GraphQLInputObjectType) {
            return areAllInputFieldsDeleted((GraphQLInputObjectType) type, plan.inputFieldsToDelete);
        }
        if (type instanceof GraphQLUnionType) {
            return areAllUnionMembersDeleted((GraphQLUnionType) type, plan.typesToDelete);
        }
        return false;
    }

    private static boolean areAllFieldsDeleted(GraphQLFieldsContainer fieldsContainer,
                                               Set<GraphQLFieldDefinition> fieldsToDelete) {
        for (GraphQLFieldDefinition fieldDefinition : fieldsContainer.getFieldDefinitions()) {
            if (!fieldsToDelete.contains(fieldDefinition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean areAllInputFieldsDeleted(GraphQLInputObjectType inputObjectType,
                                                    Set<GraphQLInputObjectField> inputFieldsToDelete) {
        for (GraphQLInputObjectField inputField : inputObjectType.getFieldDefinitions()) {
            if (!inputFieldsToDelete.contains(inputField)) {
                return false;
            }
        }
        return true;
    }

    private static boolean areAllUnionMembersDeleted(GraphQLUnionType unionType, Set<String> typesToDelete) {
        for (GraphQLNamedOutputType memberType : unionType.getTypes()) {
            if (!typesToDelete.contains(memberType.getName())) {
                return false;
            }
        }
        return true;
    }

    private boolean markElementsReferencingDeletedTypes(List<GraphQLNamedType> allTypes, DeletionPlan plan) {
        boolean changed = false;
        for (GraphQLNamedType type : allTypes) {
            if (plan.typesToDelete.contains(type.getName())) {
                continue;
            }
            if (markTypeReferencingElements(type, plan)) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean markTypeReferencingElements(GraphQLNamedType type, DeletionPlan plan) {
        if (type instanceof GraphQLFieldsContainer) {
            return markFieldsWithDeletedReturnType((GraphQLFieldsContainer) type, plan);
        }
        if (type instanceof GraphQLInputObjectType) {
            return markInputFieldsWithDeletedType((GraphQLInputObjectType) type, plan);
        }
        return false;
    }

    private boolean markFieldsWithDeletedReturnType(GraphQLFieldsContainer fieldsContainer, DeletionPlan plan) {
        boolean changed = false;
        for (GraphQLFieldDefinition fieldDefinition : fieldsContainer.getFieldDefinitions()) {
            if (plan.fieldsToDelete.contains(fieldDefinition)) {
                continue;
            }
            if (plan.typesToDelete.contains(unwrapTypeName(fieldDefinition.getType()))) {
                plan.fieldsToDelete.add(fieldDefinition);
                changed = true;
            }
        }
        return changed;
    }

    private boolean markInputFieldsWithDeletedType(GraphQLInputObjectType inputObjectType, DeletionPlan plan) {
        boolean changed = false;
        for (GraphQLInputObjectField inputField : inputObjectType.getFieldDefinitions()) {
            if (plan.inputFieldsToDelete.contains(inputField)) {
                continue;
            }
            if (plan.typesToDelete.contains(unwrapTypeName(inputField.getType()))) {
                plan.inputFieldsToDelete.add(inputField);
                changed = true;
            }
        }
        return changed;
    }

    private static String unwrapTypeName(GraphQLType type) {
        GraphQLType current = type;
        while (GraphQLTypeUtil.isWrapped(current)) {
            current = GraphQLTypeUtil.unwrapOne(current);
        }
        return ((GraphQLNamedType) current).getName();
    }

    private static class DeletionPlan {

        private final Set<String> typesToDelete = new LinkedHashSet<>();
        private final Set<GraphQLFieldDefinition> fieldsToDelete = new LinkedHashSet<>();
        private final Set<GraphQLInputObjectField> inputFieldsToDelete = new LinkedHashSet<>();
    }

    private static class ElementRemovalVisitor extends GraphQLTypeVisitorStub {

        private final DeletionPlan deletionPlan;
        private final VisibleInterfaceImplementationPredicate interfaceImplementationPredicate;

        private ElementRemovalVisitor(DeletionPlan deletionPlan,
                                      VisibleInterfaceImplementationPredicate interfaceImplementationPredicate) {
            this.deletionPlan = deletionPlan;
            this.interfaceImplementationPredicate = interfaceImplementationPredicate;
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
            if (deletionPlan.typesToDelete.contains(objectType.getName())) {
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
            if (deletionPlan.typesToDelete.contains(interfaceType.getName())) {
                return deleteNode(context);
            }
            List<GraphQLInterfaceType> visibleInterfaces = getVisibleInterfaces(interfaceType);
            if (visibleInterfaces.size() == interfaceType.getInterfaces().size()) {
                return TraversalControl.CONTINUE;
            }
            GraphQLInterfaceType changedInterfaceType = interfaceType.transform(builder -> builder.replaceInterfaces(visibleInterfaces));
            return changeNode(context, changedInterfaceType);
        }

        private List<GraphQLInterfaceType> getVisibleInterfaces(GraphQLImplementingType implementingType) {
            List<GraphQLInterfaceType> visibleInterfaces = new ArrayList<>();
            for (GraphQLNamedOutputType namedInterface : implementingType.getInterfaces()) {
                GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) namedInterface;
                if (deletionPlan.typesToDelete.contains(interfaceType.getName())) {
                    continue;
                }
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
            if (deletionPlan.typesToDelete.contains(inputObjectType.getName())) {
                return deleteNode(context);
            }
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLUnionType(GraphQLUnionType unionType, TraverserContext<GraphQLSchemaElement> context) {
            if (deletionPlan.typesToDelete.contains(unionType.getName())) {
                return deleteNode(context);
            }
            List<GraphQLNamedOutputType> visibleMembers = getVisibleUnionMembers(unionType);
            if (visibleMembers.size() == unionType.getTypes().size()) {
                return TraversalControl.CONTINUE;
            }
            GraphQLUnionType changedUnionType = unionType.transform(builder -> builder.replacePossibleTypes(visibleMembers));
            return changeNode(context, changedUnionType);
        }

        private List<GraphQLNamedOutputType> getVisibleUnionMembers(GraphQLUnionType unionType) {
            List<GraphQLNamedOutputType> visibleMembers = new ArrayList<>();
            for (GraphQLNamedOutputType memberType : unionType.getTypes()) {
                if (!deletionPlan.typesToDelete.contains(memberType.getName())) {
                    visibleMembers.add(memberType);
                }
            }
            return visibleMembers;
        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition definition,
                                                            TraverserContext<GraphQLSchemaElement> context) {
            if (deletionPlan.fieldsToDelete.contains(definition)) {
                return deleteNode(context);
            }
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField definition,
                                                             TraverserContext<GraphQLSchemaElement> context) {
            if (deletionPlan.inputFieldsToDelete.contains(definition)) {
                return deleteNode(context);
            }
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLArgument(GraphQLArgument argument,
                                                     TraverserContext<GraphQLSchemaElement> context) {
            if (deletionPlan.typesToDelete.contains(unwrapTypeName(argument.getType()))) {
                return deleteNode(context);
            }
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument argument,
                                                                     TraverserContext<GraphQLSchemaElement> context) {
            if (deletionPlan.typesToDelete.contains(unwrapTypeName(argument.getType()))) {
                return deleteNode(context);
            }
            return TraversalControl.CONTINUE;
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
