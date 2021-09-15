package graphql.schema.usage;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.ScalarInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

public class SchemaUsage {
    private final Builder builder;

    SchemaUsage(Builder builder) {
        this.builder = builder;
    }

    public Map<String, Integer> getFieldReferenceCount() {
        return builder.fieldReferenceCounts;
    }

    public Map<String, Integer> getOutputFieldReferenceCount() {
        return builder.outputFieldReferenceCounts;
    }

    public Map<String, Integer> getInputFieldReferenceCount() {
        return builder.inputFieldReferenceCounts;
    }

    public Map<String, Integer> getArgumentReferenceCount() {
        return builder.argReferenceCount;
    }

    public Map<String, Integer> getInterfaceReferenceCount() {
        return builder.interfaceReferenceCount;
    }

    public Map<String, Integer> getUnionReferenceCount() {
        return builder.unionReferenceCount;
    }

    public Map<String, Integer> getDirectiveReferenceCount() {
        return builder.directiveReferenceCount;
    }

    /**
     * This returns true if the named type is reference some where in the schema.
     *
     * @param schema   the schema that contains the name type
     * @param typeName the type name to check
     *
     * @return true if the type could be referenced
     */
    @SuppressWarnings("RedundantIfStatement")
    public boolean isReferenced(GraphQLSchema schema, String typeName) {
        return isReferencedImpl(schema, typeName, new HashSet<>());
    }

    public boolean isReferencedImpl(GraphQLSchema schema, String typeName, Set<String> checkedAlready) {
        if (ScalarInfo.isGraphqlSpecifiedScalar(typeName)) {
            return true;
        }
        GraphQLNamedType type = schema.getTypeAs(typeName);
        if (type == null) {
            return false;
        }
        if (checkedAlready.contains(typeName)) {
            return false;
        }
        if (Introspection.isIntrospectionTypes(type)) {
            return true;
        }

        if (type == schema.getQueryType()) {
            return true;
        }
        if (type == schema.getMutationType()) {
            return true;
        }
        if (type == schema.getSubscriptionType()) {
            return true;
        }

        Set<String> references = builder.typeBackReferences.getOrDefault(typeName, emptySet());
        for (String reference : references) {
            if (isReferencedImpl(schema, reference, checkedAlready)) {
                return true;
            }
        }

        checkedAlready.add(typeName);

        if (type instanceof GraphQLInterfaceType) {
            Set<String> implementors = builder.interfaceImplementors.getOrDefault(type.getName(), emptySet());
            for (String implementor : implementors) {
                if (isReferencedImpl(schema, implementor, checkedAlready)) {
                    return true;
                }
            }
        }
        if (type instanceof GraphQLObjectType) {
            List<GraphQLNamedOutputType> interfaces = ((GraphQLObjectType) type).getInterfaces();
            for (GraphQLNamedOutputType memberInterface : interfaces) {
                Set<String> implementors = builder.interfaceImplementors.getOrDefault(memberInterface.getName(), emptySet());
                for (String implementor : implementors) {
                    if (isReferencedImpl(schema, implementor, checkedAlready)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Internal
    static class Builder {
        Map<String, Integer> fieldReferenceCounts = new HashMap<>();
        Map<String, Integer> inputFieldReferenceCounts = new HashMap<>();
        Map<String, Integer> outputFieldReferenceCounts = new HashMap<>();
        Map<String, Integer> argReferenceCount = new HashMap<>();
        Map<String, Integer> interfaceReferenceCount = new HashMap<>();
        Map<String, Integer> unionReferenceCount = new HashMap<>();
        Map<String, Integer> directiveReferenceCount = new HashMap<>();
        Map<String, Set<String>> interfaceImplementors = new HashMap<>();
        Map<String, Set<String>> typeBackReferences = new HashMap<>();

        SchemaUsage build() {
            return new SchemaUsage(this);
        }
    }
}
