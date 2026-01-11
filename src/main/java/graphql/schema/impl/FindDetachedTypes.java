package graphql.schema.impl;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds detached types in a schema by performing a DFS traversal from root types
 * and directive definitions to find all reachable (attached) types, then computing the complement.
 */
@Internal
public class FindDetachedTypes {

    /**
     * Computes the set of detached types - types that exist in the typeMap but are not
     * reachable from the root types (Query, Mutation, Subscription) or directive definitions.
     *
     * @param typeMap          all types in the schema
     * @param queryType        the query root type (required)
     * @param mutationType     the mutation root type (may be null)
     * @param subscriptionType the subscription root type (may be null)
     * @param directives       directive definitions in the schema
     * @return set of types that are not reachable from root types or directives
     */
    public static Set<GraphQLNamedType> findDetachedTypes(Map<String, GraphQLNamedType> typeMap,
                                                           GraphQLObjectType queryType,
                                                           GraphQLObjectType mutationType,
                                                           GraphQLObjectType subscriptionType,
                                                           Collection<GraphQLDirective> directives) {
        int typeCount = typeMap.size();
        Set<String> attachedTypeNames = new HashSet<>(typeCount);

        // DFS from each root type to find all reachable types
        visitType(queryType, attachedTypeNames);
        if (mutationType != null) {
            visitType(mutationType, attachedTypeNames);
        }
        if (subscriptionType != null) {
            visitType(subscriptionType, attachedTypeNames);
        }

        // Also visit types reachable from directive argument definitions
        for (GraphQLDirective directive : directives) {
            visitDirective(directive, attachedTypeNames);
        }

        // Detached types = all types minus attached types
        // Use Math.max to ensure capacity is never negative (can happen if attached types include
        // types not in typeMap, like built-in scalars)
        int detachedCapacity = Math.max(0, typeCount - attachedTypeNames.size());
        Set<GraphQLNamedType> detachedTypes = new HashSet<>(detachedCapacity);
        for (GraphQLNamedType type : typeMap.values()) {
            if (!attachedTypeNames.contains(type.getName())) {
                detachedTypes.add(type);
            }
        }

        return detachedTypes;
    }

    private static void visitDirective(GraphQLDirective directive, Set<String> visited) {
        // Visit argument types in the directive definition
        for (GraphQLArgument arg : directive.getArguments()) {
            visitType(arg.getType(), visited);
        }
    }

    private static void visitType(GraphQLType type, Set<String> visited) {
        // Unwrap modifiers (NonNull, List)
        GraphQLType unwrapped = unwrapType(type);

        if (!(unwrapped instanceof GraphQLNamedType)) {
            return;
        }

        GraphQLNamedType namedType = (GraphQLNamedType) unwrapped;
        String typeName = namedType.getName();

        // Skip if already visited
        if (visited.contains(typeName)) {
            return;
        }
        visited.add(typeName);

        // Visit fields and their types recursively
        if (namedType instanceof GraphQLObjectType) {
            visitObjectType((GraphQLObjectType) namedType, visited);
        } else if (namedType instanceof GraphQLInterfaceType) {
            visitInterfaceType((GraphQLInterfaceType) namedType, visited);
        } else if (namedType instanceof GraphQLUnionType) {
            visitUnionType((GraphQLUnionType) namedType, visited);
        } else if (namedType instanceof GraphQLInputObjectType) {
            visitInputObjectType((GraphQLInputObjectType) namedType, visited);
        }
        // Scalars and Enums have no further types to visit
    }

    private static void visitObjectType(GraphQLObjectType objectType, Set<String> visited) {
        // Visit interfaces this object implements
        for (GraphQLOutputType iface : objectType.getInterfaces()) {
            visitType(iface, visited);
        }

        // Visit field types
        for (GraphQLFieldDefinition field : objectType.getFieldDefinitions()) {
            visitType(field.getType(), visited);
            // Visit argument types
            for (GraphQLArgument arg : field.getArguments()) {
                visitType(arg.getType(), visited);
            }
        }
    }

    private static void visitInterfaceType(GraphQLInterfaceType interfaceType, Set<String> visited) {
        // Visit interfaces this interface extends
        for (GraphQLOutputType iface : interfaceType.getInterfaces()) {
            visitType(iface, visited);
        }

        // Visit field types
        for (GraphQLFieldDefinition field : interfaceType.getFieldDefinitions()) {
            visitType(field.getType(), visited);
            // Visit argument types
            for (GraphQLArgument arg : field.getArguments()) {
                visitType(arg.getType(), visited);
            }
        }
    }

    private static void visitUnionType(GraphQLUnionType unionType, Set<String> visited) {
        // Visit all possible types in the union
        for (GraphQLOutputType possibleType : unionType.getTypes()) {
            visitType(possibleType, visited);
        }
    }

    private static void visitInputObjectType(GraphQLInputObjectType inputObjectType, Set<String> visited) {
        // Visit field types
        for (GraphQLInputObjectField field : inputObjectType.getFieldDefinitions()) {
            visitType(field.getType(), visited);
        }
    }

    private static GraphQLType unwrapType(GraphQLType type) {
        GraphQLType current = type;
        while (current instanceof GraphQLNonNull || current instanceof GraphQLList) {
            if (current instanceof GraphQLNonNull) {
                current = ((GraphQLNonNull) current).getWrappedType();
            } else {
                current = ((GraphQLList) current).getWrappedType();
            }
        }
        return current;
    }
}
