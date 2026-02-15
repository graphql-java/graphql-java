package graphql.schema.usage;

import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.PublicApi;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.Directives;
import graphql.schema.idl.ScalarInfo;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

/**
 * This class shows schema usage information.  There are two aspects to this.  To be strongly referenced, a schema element
 * (directive or type) must some how be connected back to the root query, mutation or subscription type.  It's possible to define
 * types that reference other types but if they do not get used by a field / interface or union that itself leads back to the root
 * types then it is not considered strongly references.
 *
 * Its reference counts however might be non-zero yet not be strongly referenced.  For example given `type A { f : B } type B { f : A }` both types A and B
 * will have non-zero counts but if no other type points to A or B that leads back to the root types then they are not strongly referenced types.
 *
 * Such types could be removed from the schema because there is no way to ever consume these types in a query.  A common use case for this class
 * is to find the types and directives in a schema that could in theory be removed because they are not useful.
 */
@PublicApi
public class SchemaUsage {
    private final Map<String, Integer> fieldReferenceCounts;
    private final Map<String, Integer> inputFieldReferenceCounts;
    private final Map<String, Integer> outputFieldReferenceCounts;
    private final Map<String, Integer> argReferenceCount;
    private final Map<String, Integer> interfaceReferenceCount;
    private final Map<String, Integer> unionReferenceCount;
    private final Map<String, Integer> directiveReferenceCount;
    private final Map<String, Set<String>> interfaceImplementors;
    private final Map<String, Set<String>> elementBackReferences;

    private final Map<String, Set<String>> unionReferences;

    private SchemaUsage(Builder builder) {
        this.fieldReferenceCounts = ImmutableMap.copyOf(builder.fieldReferenceCounts);
        this.inputFieldReferenceCounts = ImmutableMap.copyOf(builder.inputFieldReferenceCounts);
        this.outputFieldReferenceCounts = ImmutableMap.copyOf(builder.outputFieldReferenceCounts);
        this.argReferenceCount = ImmutableMap.copyOf(builder.argReferenceCount);
        this.interfaceReferenceCount = ImmutableMap.copyOf(builder.interfaceReferenceCount);
        this.unionReferenceCount = ImmutableMap.copyOf(builder.unionReferenceCount);
        this.directiveReferenceCount = ImmutableMap.copyOf(builder.directiveReferenceCount);
        this.interfaceImplementors = ImmutableMap.copyOf(builder.interfaceImplementors);
        this.elementBackReferences = ImmutableMap.copyOf(builder.elementBackReferences);
        this.unionReferences = ImmutableMap.copyOf(builder.unionReferences);
    }

    /**
     * This shows how many times a type is referenced by either an input or output field.
     *
     * @return a map of type name to field reference counts
     */
    public Map<String, Integer> getFieldReferenceCounts() {
        return fieldReferenceCounts;
    }

    /**
     * This shows how many times a type is referenced by an output field.
     *
     * @return a map of type name to output field reference counts
     */
    public Map<String, Integer> getOutputFieldReferenceCounts() {
        return outputFieldReferenceCounts;
    }

    /**
     * This shows how many times a type is referenced by an input field.
     *
     * @return a map of type name to input field reference counts
     */
    public Map<String, Integer> getInputFieldReferenceCounts() {
        return inputFieldReferenceCounts;
    }

    /**
     * This shows how many times a type is referenced by an argument.
     *
     * @return a map of type name to argument reference counts
     */
    public Map<String, Integer> getArgumentReferenceCounts() {
        return argReferenceCount;
    }

    /**
     * This shows how many times an interface type is referenced as a member in some other
     * object or interface type.
     *
     * @return a map of interface type name to object or interface type reference counts
     */
    public Map<String, Integer> getInterfaceReferenceCounts() {
        return interfaceReferenceCount;
    }

    /**
     * This shows how many times an object type is referenced as a member in some other
     * union type.
     *
     * @return a map of object type name to union membership reference counts
     */
    public Map<String, Integer> getUnionReferenceCounts() {
        return unionReferenceCount;
    }

    /**
     * This shows how many times a directive is applied on some other schema element.
     *
     * @return a map of directive name to applied directive counts
     */
    public Map<String, Integer> getDirectiveReferenceCounts() {
        return directiveReferenceCount;
    }

    /**
     * Returns true if the named element is strongly reference somewhere in the schema back to the root types such as the schema
     * query, mutation or subscription types.
     *
     * Graphql specified scalar types, introspection types and directives are always counted as referenced, even if
     * not used explicitly.
     *
     * Directives that are defined but never applied on any schema elements will not report as referenced.
     *
     * @param schema      the schema that contains the name type
     * @param elementName the element name to check
     *
     * @return true if the element could be referenced
     */
    public boolean isStronglyReferenced(GraphQLSchema schema, String elementName) {
        return isReferencedImpl(schema, elementName, new HashSet<>());
    }

    /**
     * This returns all the unreferenced named elements in a schema.
     *
     * @param schema the schema to check
     *
     * @return a set of the named schema elements where {@link #isStronglyReferenced(GraphQLSchema, String)} returns false
     */
    public Set<GraphQLNamedSchemaElement> getUnReferencedElements(GraphQLSchema schema) {
        Set<GraphQLNamedSchemaElement> elements = new LinkedHashSet<>();
        schema.getAllTypesAsList().forEach(type -> {
            if (!isStronglyReferenced(schema, type.getName())) {
                elements.add(type);
            }
        });
        schema.getDirectives().forEach(directive -> {
            if (!isStronglyReferenced(schema, directive.getName())) {
                elements.add(directive);
            }
        });
        return elements;
    }

    private boolean isReferencedImpl(GraphQLSchema schema, String elementName, Set<String> pathSoFar) {
        if (pathSoFar.contains(elementName)) {
            return false; // circular reference to that element
        }
        pathSoFar.add(elementName);

        if (ScalarInfo.isGraphqlSpecifiedScalar(elementName)) {
            return true;
        }

        GraphQLDirective directive = schema.getDirective(elementName);
        if (directive != null) {
            String directiveName = directive.getName();
            if (Directives.isBuiltInDirective(directiveName)) {
                return true;
            }
            if (isNamedElementReferenced(schema, directiveName, pathSoFar)) {
                return true;
            }
        }

        GraphQLNamedType type = schema.getTypeAs(elementName);
        if (type == null) {
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

        if (isNamedElementReferenced(schema, elementName, pathSoFar)) {
            return true;
        }

        if (type instanceof GraphQLInterfaceType) {
            Set<String> implementors = interfaceImplementors.getOrDefault(type.getName(), emptySet());
            for (String implementor : implementors) {
                if (isReferencedImpl(schema, implementor, pathSoFar)) {
                    return true;
                }
            }
        }
        if (type instanceof GraphQLObjectType) {
            List<GraphQLNamedOutputType> interfaces = ((GraphQLObjectType) type).getInterfaces();
            for (GraphQLNamedOutputType memberInterface : interfaces) {
                Set<String> implementors = interfaceImplementors.getOrDefault(memberInterface.getName(), emptySet());
                for (String implementor : implementors) {
                    if (isReferencedImpl(schema, implementor, pathSoFar)) {
                        return true;
                    }
                }
            }

            Set<String> unionContainers = unionReferences.getOrDefault(type.getName(), emptySet());
            for (String unionContainer : unionContainers) {
                if (isReferencedImpl(schema, unionContainer, pathSoFar)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNamedElementReferenced(GraphQLSchema schema, String elementName, Set<String> pathSoFar) {
        Set<String> references = elementBackReferences.getOrDefault(elementName, emptySet());
        for (String reference : references) {
            if (isReferencedImpl(schema, reference, pathSoFar)) {
                return true;
            }
        }
        return false;
    }

    @Internal
    static class Builder {
        Map<String, Integer> fieldReferenceCounts = new LinkedHashMap<>();
        Map<String, Integer> inputFieldReferenceCounts = new LinkedHashMap<>();
        Map<String, Integer> outputFieldReferenceCounts = new LinkedHashMap<>();
        Map<String, Integer> argReferenceCount = new LinkedHashMap<>();
        Map<String, Integer> interfaceReferenceCount = new LinkedHashMap<>();
        Map<String, Integer> unionReferenceCount = new LinkedHashMap<>();
        Map<String, Integer> directiveReferenceCount = new LinkedHashMap<>();
        Map<String, Set<String>> interfaceImplementors = new LinkedHashMap<>();

        Map<String, Set<String>> unionReferences = new LinkedHashMap<>();
        Map<String, Set<String>> elementBackReferences = new LinkedHashMap<>();

        SchemaUsage build() {
            return new SchemaUsage(this);
        }
    }
}
