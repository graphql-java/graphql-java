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
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
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
        if (isNotTypeReference(node.getName())) {
            assertTypeUniqueness(node, result);
        } else {
            save(node.getName(), node);
        }
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        if (isNotTypeReference(node.getName())) {
            assertTypeUniqueness(node, result);
        } else {
            save(node.getName(), node);
        }
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
        if (isNotTypeReference(node.getName())) {
            assertTypeUniqueness(node, result);
        } else {
            save(node.getName(), node);
        }
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

    private <T> void saveIndirectStrongReference(Supplier<GraphQLType> typeSupplier) {
        GraphQLNamedType type = unwrapAllAs(typeSupplier.get());
        if (!(type instanceof GraphQLTypeReference)) {
            indirectStrongReferences.put(type.getName(), type);
        }
    }


    private boolean isNotTypeReference(String name) {
        return result.containsKey(name) && !(result.get(name) instanceof GraphQLTypeReference);
    }

    private void save(String name, GraphQLNamedType type) {
        result.put(name, type);
    }


    /*
        From https://spec.graphql.org/October2021/#sec-Type-System

           All types within a GraphQL schema must have unique names. No two provided types may have the same name.
           No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).

        Enforcing this helps avoid problems later down the track fo example https://github.com/graphql-java/graphql-java/issues/373
    */
    private void assertTypeUniqueness(GraphQLNamedType type, Map<String, GraphQLNamedType> result) {
        GraphQLType existingType = result.get(type.getName());
        // do we have an existing definition
        if (existingType != null) {
            // type references are ok
            if (!(existingType instanceof GraphQLTypeReference || type instanceof GraphQLTypeReference)) {
                assertUniqueTypeObjects(type, existingType);
            }
        }
    }

    private void assertUniqueTypeObjects(GraphQLNamedType type, GraphQLType existingType) {
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
     * It's possible for certain schema edits to create a situation where a field / arg / input field had a type reference, then
     * it got replaced with an actual strong reference and then the schema gets edited such that the only reference
     * to that type is the replaced strong reference.  This edge case means that the replaced reference can be
     * missed if it's the only way to get to that type because this visitor asks for the children as original type,
     * e.g. the type reference and not the replaced reference.
     *
     * @param visitedTypes the types collected by this visitor
     *
     * @return a fixed up map where the only
     */
    private Map<String, GraphQLNamedType> fixDanglingReplacedTypes(Map<String, GraphQLNamedType> visitedTypes) {
        for (GraphQLNamedType indirectStrongReference : indirectStrongReferences.values()) {
            String typeName = indirectStrongReference.getName();
            visitedTypes.putIfAbsent(typeName, indirectStrongReference);
        }
        return visitedTypes;
    }
}
