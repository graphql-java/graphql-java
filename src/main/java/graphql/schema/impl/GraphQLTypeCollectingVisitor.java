package graphql.schema.impl;

import com.google.common.collect.ImmutableMap;
import graphql.AssertException;
import graphql.Internal;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import static graphql.schema.GraphQLTypeUtil.unwrapAllAs;
import static graphql.util.TraversalControl.CONTINUE;
import static java.lang.String.format;

/**
 * A visitor that collects all {@link GraphQLNamedType}s during schema traversal.
 * <p>
 * This visitor must be used with a traverser that calls {@code getChildrenWithTypeReferences()}
 * to get children (see {@link SchemaUtil#visitPartiallySchema}). This means that when a field,
 * argument, or input field references a type via {@link GraphQLTypeReference}, the traverser
 * will see the type reference as a child, not the actual type it points to. Type references
 * themselves are not collected - only concrete type instances are stored in the result map.
 * <p>
 * Because type references are not followed, this visitor also tracks "indirect strong references"
 * - types that are directly referenced (not via type reference) by fields, arguments, and input
 * fields. This handles edge cases where schema transformations replace type references with
 * actual types, which would otherwise be missed during traversal.
 *
 * @see SchemaUtil#visitPartiallySchema
 * @see #fixDanglingReplacedTypes
 */
@Internal
public class GraphQLTypeCollectingVisitor extends GraphQLTypeVisitorStub {

    private final Map<String, GraphQLNamedType> result = new LinkedHashMap<>();
    private final Map<String, GraphQLNamedType> indirectStrongReferences = new LinkedHashMap<>();

    public GraphQLTypeCollectingVisitor() {
    }

    @Override
    public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context) {
        assertTypeUniqueness(node, result);
        save(node.getName(), node);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
        assertTypeUniqueness(node, result);
        save(node.getName(), node);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        assertTypeUniqueness(node, result);
        save(node.getName(), node);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        assertTypeUniqueness(node, result);
        save(node.getName(), node);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
        assertTypeUniqueness(node, result);
        save(node.getName(), node);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {
        assertTypeUniqueness(node, result);
        save(node.getName(), node);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        saveIndirectStrongReference(node::getType);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
        saveIndirectStrongReference(node::getType);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        saveIndirectStrongReference(node::getType);
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
        saveIndirectStrongReference(node::getType);
        return CONTINUE;
    }

    private void saveIndirectStrongReference(Supplier<GraphQLType> typeSupplier) {
        GraphQLNamedType type = unwrapAllAs(typeSupplier.get());
        if (!(type instanceof GraphQLTypeReference)) {
            indirectStrongReferences.put(type.getName(), type);
        }
    }

    private void save(String name, GraphQLNamedType type) {
        result.put(name, type);
    }

    private void assertTypeUniqueness(GraphQLNamedType type, Map<String, GraphQLNamedType> result) {
        GraphQLNamedType existingType = result.get(type.getName());
        if (existingType != null) {
            assertUniqueTypeObjects(type, existingType);
        }
    }

    private void assertUniqueTypeObjects(GraphQLNamedType type, GraphQLNamedType existingType) {
        // object comparison here is deliberate
        if (existingType != type) {
            throw new AssertException(format("All types within a GraphQL schema must have unique names. No two provided types may have the same name.\n" +
                            "No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).\n" +
                            "You have redefined the type '%s' from being a '%s' to a '%s'",
                    type.getName(), existingType.getClass().getSimpleName(), type.getClass().getSimpleName()));
        }
    }

    public ImmutableMap<String, GraphQLNamedType> getResult() {
        Map<String, GraphQLNamedType> types = new TreeMap<>(fixDanglingReplacedTypes(result));
        return ImmutableMap.copyOf(types);
    }

    /**
     * Fixes an edge case where types might be missed during traversal due to replaced type references.
     * <p>
     * The problem: During schema construction or transformation, a field's type might initially
     * be a {@link GraphQLTypeReference} (a placeholder). Later, this reference gets replaced with the
     * actual type instance. However, the schema traverser uses {@code getChildrenWithTypeReferences()}
     * to discover children, which returns the original type references, not the replaced strong references.
     * <p>
     * The scenario:
     * <ol>
     *   <li>Field {@code foo} has type reference to {@code Bar}</li>
     *   <li>During schema editing, the reference is replaced with the actual {@code Bar} type</li>
     *   <li>Further edits remove all other paths to {@code Bar}</li>
     *   <li>Now the only way to reach {@code Bar} is via the replaced reference in {@code foo}</li>
     *   <li>But the traverser still sees the original type reference as the child, so {@code Bar}
     *       is never visited as a child node</li>
     * </ol>
     * <p>
     * The fix: During traversal, we also capture types directly from fields/arguments/inputs
     * (in {@link #indirectStrongReferences}). After traversal, we merge any types that were captured
     * this way but weren't found through normal traversal.
     *
     * @param visitedTypes the types collected through normal traversal
     *
     * @return the fixed map including any dangling replaced types
     */
    private Map<String, GraphQLNamedType> fixDanglingReplacedTypes(Map<String, GraphQLNamedType> visitedTypes) {
        for (GraphQLNamedType indirectStrongReference : indirectStrongReferences.values()) {
            String typeName = indirectStrongReference.getName();
            visitedTypes.putIfAbsent(typeName, indirectStrongReference);
        }
        return visitedTypes;
    }
}
