package graphql.schema.impl;

import graphql.Internal;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLNamedType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects type-refs found in type- and directive-definitions for later replacement with actual types.
 * This class performs shallow scans (no recursive traversal from one type-def to another) and
 * collects replacement targets that need their type references resolved.
 */
@Internal
public class ShallowTypeRefCollector {

    // Replacement targets - no common supertype exists for the replacement-target classes,
    // so we use Object. Target classes include: GraphQLArgument, GraphQLFieldDefinition,
    // GraphQLInputObjectField, GraphQLList, GraphQLNonNull, GraphQLUnionType,
    // GraphQLObjectType (for interfaces), GraphQLInterfaceType (for interfaces),
    // GraphQLAppliedDirectiveArgument
    private final List<Object> replaceTargets = new ArrayList<>();

    /**
     * Scan a type definition for type references.
     * Called on GraphQL{Object|Input|Scalar|Union|etc}Type - NOT on wrappers or type-refs.
     *
     * @param type the named type to scan
     */
    public void handleTypeDef(GraphQLNamedType type) {
        // Phase 1: Scalars without applied directives - nothing to do yet.
        // Future phases will scan fields, arguments, interfaces, union members,
        // applied directives, etc.
    }

    /**
     * Scan a directive definition for type references in its arguments.
     *
     * @param directive the directive definition to scan
     */
    public void handleDirective(GraphQLDirective directive) {
        // Phase 2+: Will scan directive arguments for type references
    }

    /**
     * Replace all collected type references with actual types from typeMap.
     * After this call, no GraphQLTypeReference should remain in the schema.
     *
     * @param typeMap the map of type names to actual types
     * @throws graphql.AssertException if a referenced type is not found in typeMap
     */
    public void replaceTypes(Map<String, GraphQLNamedType> typeMap) {
        // Phase 1: No type references to replace yet.
        // Future phases will iterate replaceTargets and call appropriate replace methods.
    }
}
