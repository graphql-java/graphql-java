package graphql.schema.impl;

import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLTypeUtil;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
@NullMarked
public class OneOfInputTypeReachability {

    public static ImmutableMap<String, Boolean> compute(Map<String, GraphQLNamedType> typeMap) {
        Map<String, Boolean> containsOneOf = new LinkedHashMap<>();
        Deque<String> pending = new ArrayDeque<>();
        for (GraphQLNamedType type : typeMap.values()) {
            if (!(type instanceof GraphQLInputObjectType)) {
                continue;
            }
            boolean oneOf = ((GraphQLInputObjectType) type).isOneOf();
            containsOneOf.put(type.getName(), oneOf);
            if (oneOf) {
                pending.add(type.getName());
            }
        }
        if (pending.isEmpty()) {
            return ImmutableMap.copyOf(containsOneOf);
        }
        Map<String, List<String>> parents = new LinkedHashMap<>();
        for (GraphQLNamedType type : typeMap.values()) {
            if (type instanceof GraphQLInputObjectType) {
                collectParents((GraphQLInputObjectType) type, parents);
            }
        }
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            markParents(parents.getOrDefault(name, List.of()), containsOneOf, pending);
        }
        return ImmutableMap.copyOf(containsOneOf);
    }

    private static void markParents(List<String> parents, Map<String, Boolean> containsOneOf, Deque<String> pending) {
        for (String parent : parents) {
            if (!Boolean.TRUE.equals(containsOneOf.get(parent))) {
                containsOneOf.put(parent, true);
                pending.addLast(parent);
            }
        }
    }

    private static void collectParents(GraphQLInputObjectType type, Map<String, List<String>> parents) {
        for (GraphQLInputObjectField field : type.getFields()) {
            GraphQLNamedType child = GraphQLTypeUtil.unwrapAllAs(field.getType());
            parents.computeIfAbsent(child.getName(), ignored -> new ArrayList<>()).add(type.getName());
        }
    }
}
