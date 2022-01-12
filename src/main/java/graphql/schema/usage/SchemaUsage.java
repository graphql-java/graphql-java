package graphql.schema.usage;

import com.google.common.collect.ImmutableMap;
import graphql.Directives;
import graphql.Internal;
import graphql.PublicApi;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLDirective;
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

@PublicApi
public class SchemaUsage {
    private final Builder builder;

    SchemaUsage(Builder builder) {
        this.builder = builder;
    }

    /**
     * This shows how many times a type is referenced by either an input or output field.
     * <p>
     * Note if the count is greater than zero it could still not be a referenced
     * type because of it may be part of the reference chain that does not lead back
     * to the root query / mutation or subscription type.
     * </p>
     *
     * @return a map of type name to field reference counts
     */
    public Map<String, Integer> getFieldReferenceCounts() {
        return ImmutableMap.copyOf(builder.fieldReferenceCounts);
    }

    /**
     * This shows how many times a type is referenced by an output field.
     * <p>
     * Note if the count is greater than zero it could still not be a referenced
     * type because of it may be part of the reference chain that does not lead back
     * to the root query / mutation or subscription type.
     * </p>
     *
     * @return a map of type name to output field reference counts
     */
    public Map<String, Integer> getOutputFieldReferenceCounts() {
        return ImmutableMap.copyOf(builder.outputFieldReferenceCounts);
    }

    /**
     * This shows how many times a type is referenced by an input field.
     * <p>
     * Note if the count is greater than zero it could still not be a referenced
     * type because of it may be part of the reference chain that does not lead back
     * to the root query / mutation or subscription type.
     * </p>
     *
     * @return a map of type name to input field reference counts
     */
    public Map<String, Integer> getInputFieldReferenceCounts() {
        return ImmutableMap.copyOf(builder.inputFieldReferenceCounts);
    }

    /**
     * This shows how many times a type is referenced by an argument.
     * <p>
     * Note if the count is greater than zero it could still not be a referenced
     * type because of it may be part of the reference chain that does not lead back
     * to the root query / mutation or subscription type.
     * </p>
     *
     * @return a map of type name to argument reference counts
     */
    public Map<String, Integer> getArgumentReferenceCounts() {
        return ImmutableMap.copyOf(builder.argReferenceCount);
    }

    /**
     * This shows how many times an interface type is referenced as a member in some other
     * object or interface type.
     * <p>
     * Note if the count is greater than zero it could still not be a referenced
     * type because of it may be part of the reference chain that does not lead back
     * to the root query / mutation or subscription type.
     * </p>
     *
     * @return a map of interface type name to object or interface type reference counts
     */
    public Map<String, Integer> getInterfaceReferenceCounts() {
        return ImmutableMap.copyOf(builder.interfaceReferenceCount);
    }

    /**
     * This shows how many times an object type is referenced as a member in some other
     * union type.
     * <p>
     * Note if the count is greater than zero it could still not be a referenced
     * type because of it may be part of the reference chain that does not lead back
     * to the root query / mutation or subscription type.
     * </p>
     *
     * @return a map of object type name to union membership reference counts
     */
    public Map<String, Integer> getUnionReferenceCounts() {
        return ImmutableMap.copyOf(builder.unionReferenceCount);
    }

    /**
     * This shows how many times a directive is applied on some other schema element.
     * <p>
     * Note if the count is greater than zero it could still not be a referenced
     * directive because of it may be part of the reference chain that does not lead back
     * to the root query / mutation or subscription type.
     * </p>
     *
     * @return a map of directive name to applied directive counts
     */
    public Map<String, Integer> getDirectiveReferenceCounts() {
        return ImmutableMap.copyOf(builder.directiveReferenceCount);
    }

    /**
     * Returns true if the named element is reference somewhere in the schema back to the root types such as the schema
     * query, mutation or subscription types.
     *
     * Graphql specified scalar types, introspection types and directives are always counted as referenced, even if
     * not used explicitly.
     *
     * Directives that are defined but never applied on any schema elements will nor report as referenced.
     *
     * @param schema      the schema that contains the name type
     * @param elementName the element name to check
     *
     * @return true if the element could be referenced
     */
    public boolean isReferenced(GraphQLSchema schema, String elementName) {
        return isReferencedImpl(schema, elementName, new HashSet<>(), new HashSet<>());
    }

    private boolean isReferencedImpl(GraphQLSchema schema, String elementName, Set<String> missCache, Set<String> pathSoFar) {
        if (pathSoFar.contains(elementName)) {
            return false; // circular reference to that element
        }
        pathSoFar.add(elementName);

        if (ScalarInfo.isGraphqlSpecifiedScalar(elementName)) {
            return true;
        }

        List<GraphQLDirective> directives = schema.getDirectives(elementName);
        if (!directives.isEmpty()) {
            String directiveName = directives.get(0).getName();
            if (Directives.isGraphqlSpecifiedDirective(directiveName)) {
                return true;
            }
            if (isNamedElementReferenced(schema, directiveName, missCache, pathSoFar)) {
                return true;
            }
        }

        GraphQLNamedType type = schema.getTypeAs(elementName);
        if (type == null) {
            return false;
        }
        if (missCache.contains(elementName)) {
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

        if (isNamedElementReferenced(schema, elementName, missCache, pathSoFar)) {
            return true;
        }

        if (type instanceof GraphQLInterfaceType) {
            Set<String> implementors = builder.interfaceImplementors.getOrDefault(type.getName(), emptySet());
            for (String implementor : implementors) {
                if (isReferencedImpl(schema, implementor, missCache, pathSoFar)) {
                    return true;
                }
            }
        }
        if (type instanceof GraphQLObjectType) {
            List<GraphQLNamedOutputType> interfaces = ((GraphQLObjectType) type).getInterfaces();
            for (GraphQLNamedOutputType memberInterface : interfaces) {
                Set<String> implementors = builder.interfaceImplementors.getOrDefault(memberInterface.getName(), emptySet());
                for (String implementor : implementors) {
                    if (isReferencedImpl(schema, implementor, missCache, pathSoFar)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNamedElementReferenced(GraphQLSchema schema, String elementName, Set<String> missCache, Set<String> pathSoFar) {
        Set<String> references = builder.typeBackReferences.getOrDefault(elementName, emptySet());
        for (String reference : references) {
            if (isReferencedImpl(schema, reference, missCache, pathSoFar)) {
                return true;
            }
        }
        missCache.add(elementName);
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
