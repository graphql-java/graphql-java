package graphql.schema;

import graphql.AssertException;
import graphql.Internal;

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
        if (type instanceof GraphQLInputObjectType) {
            handleInputObjectType((GraphQLInputObjectType) type);
        }
        // Scan applied directives on all directive container types
        if (type instanceof GraphQLDirectiveContainer) {
            scanAppliedDirectives(((GraphQLDirectiveContainer) type).getAppliedDirectives());
        }
        // Future phases will handle: GraphQLObjectType fields, GraphQLInterfaceType fields,
        // GraphQLUnionType members, GraphQLObjectType/InterfaceType interfaces
    }

    private void handleInputObjectType(GraphQLInputObjectType inputType) {
        for (GraphQLInputObjectField field : inputType.getFieldDefinitions()) {
            if (containsTypeReference(field.getType())) {
                replaceTargets.add(field);
            }
            // Scan applied directives on input fields
            scanAppliedDirectives(field.getAppliedDirectives());
        }
    }

    /**
     * Scan applied directives for type references in their arguments.
     *
     * @param appliedDirectives the applied directives to scan
     */
    public void scanAppliedDirectives(List<GraphQLAppliedDirective> appliedDirectives) {
        for (GraphQLAppliedDirective applied : appliedDirectives) {
            for (GraphQLAppliedDirectiveArgument arg : applied.getArguments()) {
                if (containsTypeReference(arg.getType())) {
                    replaceTargets.add(arg);
                }
            }
        }
    }

    /**
     * Scan a directive definition for type references in its arguments.
     *
     * @param directive the directive definition to scan
     */
    public void handleDirective(GraphQLDirective directive) {
        for (GraphQLArgument argument : directive.getArguments()) {
            scanArgumentType(argument);
        }
    }

    /**
     * Scan an argument's type for type references.
     */
    private void scanArgumentType(GraphQLArgument argument) {
        if (containsTypeReference(argument.getType())) {
            replaceTargets.add(argument);
        }
    }

    /**
     * Check if a type contains a type reference (possibly wrapped in List/NonNull).
     */
    private boolean containsTypeReference(GraphQLType type) {
        GraphQLType unwrapped = type;
        while (unwrapped instanceof GraphQLNonNull) {
            unwrapped = ((GraphQLNonNull) unwrapped).getWrappedType();
        }
        while (unwrapped instanceof GraphQLList) {
            unwrapped = ((GraphQLList) unwrapped).getWrappedType();
            while (unwrapped instanceof GraphQLNonNull) {
                unwrapped = ((GraphQLNonNull) unwrapped).getWrappedType();
            }
        }
        return unwrapped instanceof GraphQLTypeReference;
    }

    /**
     * Replace all collected type references with actual types from typeMap.
     * After this call, no GraphQLTypeReference should remain in the schema.
     *
     * @param typeMap the map of type names to actual types
     * @throws graphql.AssertException if a referenced type is not found in typeMap
     */
    public void replaceTypes(Map<String, GraphQLNamedType> typeMap) {
        for (Object target : replaceTargets) {
            if (target instanceof GraphQLArgument) {
                replaceArgumentType((GraphQLArgument) target, typeMap);
            } else if (target instanceof GraphQLInputObjectField) {
                replaceInputFieldType((GraphQLInputObjectField) target, typeMap);
            } else if (target instanceof GraphQLAppliedDirectiveArgument) {
                replaceAppliedDirectiveArgumentType((GraphQLAppliedDirectiveArgument) target, typeMap);
            }
            // Future phases will handle other target types
        }
    }

    private void replaceAppliedDirectiveArgumentType(GraphQLAppliedDirectiveArgument arg, Map<String, GraphQLNamedType> typeMap) {
        GraphQLInputType resolvedType = resolveInputType(arg.getType(), typeMap);
        arg.replaceType(resolvedType);
    }

    private void replaceInputFieldType(GraphQLInputObjectField field, Map<String, GraphQLNamedType> typeMap) {
        GraphQLInputType resolvedType = resolveInputType(field.getType(), typeMap);
        field.replaceType(resolvedType);
    }

    private void replaceArgumentType(GraphQLArgument argument, Map<String, GraphQLNamedType> typeMap) {
        GraphQLInputType resolvedType = resolveInputType(argument.getType(), typeMap);
        argument.replaceType(resolvedType);
    }

    /**
     * Resolve an input type, replacing any type references with actual types.
     * Handles List and NonNull wrappers recursively.
     */
    private GraphQLInputType resolveInputType(GraphQLInputType type, Map<String, GraphQLNamedType> typeMap) {
        if (type instanceof GraphQLNonNull) {
            GraphQLNonNull nonNull = (GraphQLNonNull) type;
            GraphQLType wrappedType = nonNull.getWrappedType();
            if (wrappedType instanceof GraphQLInputType) {
                GraphQLInputType resolvedWrapped = resolveInputType((GraphQLInputType) wrappedType, typeMap);
                if (resolvedWrapped != wrappedType) {
                    nonNull.replaceType(resolvedWrapped);
                }
            }
            return type;
        }
        if (type instanceof GraphQLList) {
            GraphQLList list = (GraphQLList) type;
            GraphQLType wrappedType = list.getWrappedType();
            if (wrappedType instanceof GraphQLInputType) {
                GraphQLInputType resolvedWrapped = resolveInputType((GraphQLInputType) wrappedType, typeMap);
                if (resolvedWrapped != wrappedType) {
                    list.replaceType(resolvedWrapped);
                }
            }
            return type;
        }
        if (type instanceof GraphQLTypeReference) {
            String typeName = ((GraphQLTypeReference) type).getName();
            GraphQLNamedType resolved = typeMap.get(typeName);
            if (resolved == null) {
                throw new AssertException(String.format("Type '%s' not found in schema", typeName));
            }
            if (!(resolved instanceof GraphQLInputType)) {
                throw new AssertException(String.format("Type '%s' is not an input type", typeName));
            }
            return (GraphQLInputType) resolved;
        }
        // Already a concrete type, return as-is
        return type;
    }
}
