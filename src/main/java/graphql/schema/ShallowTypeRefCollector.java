package graphql.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.AssertException;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Collects type-refs found in type- and directive-definitions for later replacement with actual types.
 * This class performs shallow scans (no recursive traversal from one type-def to another) and
 * collects replacement targets that need their type references resolved.
 * Also tracks interface-to-implementation relationships.
 */
@Internal
@NullMarked
public class ShallowTypeRefCollector {

    // Replacement targets - no common supertype exists for the replacement-target classes,
    // so we use Object. Target classes include: GraphQLArgument, GraphQLFieldDefinition,
    // GraphQLInputObjectField, GraphQLList, GraphQLNonNull, GraphQLUnionType,
    // GraphQLObjectType (for interfaces), GraphQLInterfaceType (for interfaces),
    // GraphQLAppliedDirectiveArgument
    private final List<Object> replaceTargets = new ArrayList<>();

    // Track interface implementations: interface name -> sorted set of implementing object type names
    private final Map<String, TreeSet<String>> interfaceToObjectTypeNames = new LinkedHashMap<>();

    /**
     * Scan a type definition for type references.
     * Called on GraphQL{Object|Input|Scalar|Union|etc}Type - NOT on wrappers or type-refs.
     *
     * @param type the named type to scan
     */
    public void handleTypeDef(GraphQLNamedType type) {
        if (type instanceof GraphQLInputObjectType) {
            handleInputObjectType((GraphQLInputObjectType) type);
        } else if (type instanceof GraphQLObjectType) {
            handleObjectType((GraphQLObjectType) type);
        } else if (type instanceof GraphQLInterfaceType) {
            handleInterfaceType((GraphQLInterfaceType) type);
        }
        // Scan applied directives on all directive container types
        if (type instanceof GraphQLDirectiveContainer) {
            scanAppliedDirectives(((GraphQLDirectiveContainer) type).getAppliedDirectives());
        }
        if (type instanceof GraphQLUnionType) {
            handleUnionType((GraphQLUnionType) type);
        }
    }

    private void handleObjectType(GraphQLObjectType objectType) {
        // Scan fields for type references
        for (GraphQLFieldDefinition field : objectType.getFieldDefinitions()) {
            if (containsTypeReference(field.getType())) {
                replaceTargets.add(field);
            }
            // Scan field arguments
            for (GraphQLArgument arg : field.getArguments()) {
                scanArgumentType(arg);
            }
            // Scan applied directives on field
            scanAppliedDirectives(field.getAppliedDirectives());
        }
        // Track interface implementations and scan for type references
        for (GraphQLNamedOutputType iface : objectType.getInterfaces()) {
            String interfaceName = iface.getName();
            interfaceToObjectTypeNames
                    .computeIfAbsent(interfaceName, k -> new TreeSet<>())
                    .add(objectType.getName());
        }
        if (hasInterfaceTypeReferences(objectType.getInterfaces())) {
            replaceTargets.add(new ObjectInterfaceReplaceTarget(objectType));
        }
    }

    private boolean hasInterfaceTypeReferences(List<GraphQLNamedOutputType> interfaces) {
        for (GraphQLNamedOutputType iface : interfaces) {
            if (iface instanceof GraphQLTypeReference) {
                return true;
            }
        }
        return false;
    }

    private void handleInterfaceType(GraphQLInterfaceType interfaceType) {
        // Scan fields for type references (same as object types)
        for (GraphQLFieldDefinition field : interfaceType.getFieldDefinitions()) {
            if (containsTypeReference(field.getType())) {
                replaceTargets.add(field);
            }
            // Scan field arguments
            for (GraphQLArgument arg : field.getArguments()) {
                scanArgumentType(arg);
            }
            // Scan applied directives on field
            scanAppliedDirectives(field.getAppliedDirectives());
        }
        // Interfaces can extend other interfaces
        if (hasInterfaceTypeReferences(interfaceType.getInterfaces())) {
            replaceTargets.add(new InterfaceInterfaceReplaceTarget(interfaceType));
        }
    }

    /**
     * Wrapper class to track object types that need interface replacement.
     */
    static class ObjectInterfaceReplaceTarget {
        final GraphQLObjectType objectType;

        ObjectInterfaceReplaceTarget(GraphQLObjectType objectType) {
            this.objectType = objectType;
        }
    }

    /**
     * Wrapper class to track interface types that need interface replacement.
     */
    static class InterfaceInterfaceReplaceTarget {
        final GraphQLInterfaceType interfaceType;

        InterfaceInterfaceReplaceTarget(GraphQLInterfaceType interfaceType) {
            this.interfaceType = interfaceType;
        }
    }

    private void handleUnionType(GraphQLUnionType unionType) {
        // Check if any possible types are type references
        for (GraphQLNamedOutputType possibleType : unionType.getTypes()) {
            if (possibleType instanceof GraphQLTypeReference) {
                replaceTargets.add(new UnionTypesReplaceTarget(unionType));
                break;
            }
        }
    }

    /**
     * Wrapper class to track union types that need member type replacement.
     */
    static class UnionTypesReplaceTarget {
        final GraphQLUnionType unionType;

        UnionTypesReplaceTarget(GraphQLUnionType unionType) {
            this.unionType = unionType;
        }
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
     * <p>
     * <b>Important:</b> This method mutates the type objects that were scanned via
     * {@link #handleTypeDef(GraphQLNamedType)} and {@link #handleDirective(GraphQLDirective)}.
     * The same type instances cannot be reused to build another schema after this method
     * has been called. If you need to build multiple schemas with the same types, you must
     * create new type instances for each schema.
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
            } else if (target instanceof GraphQLFieldDefinition) {
                replaceFieldType((GraphQLFieldDefinition) target, typeMap);
            } else if (target instanceof ObjectInterfaceReplaceTarget) {
                replaceObjectInterfaces((ObjectInterfaceReplaceTarget) target, typeMap);
            } else if (target instanceof InterfaceInterfaceReplaceTarget) {
                replaceInterfaceInterfaces((InterfaceInterfaceReplaceTarget) target, typeMap);
            } else if (target instanceof UnionTypesReplaceTarget) {
                replaceUnionTypes((UnionTypesReplaceTarget) target, typeMap);
            }
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

    private void replaceFieldType(GraphQLFieldDefinition field, Map<String, GraphQLNamedType> typeMap) {
        GraphQLOutputType resolvedType = resolveOutputType(field.getType(), typeMap);
        field.replaceType(resolvedType);
    }

    private void replaceObjectInterfaces(ObjectInterfaceReplaceTarget target, Map<String, GraphQLNamedType> typeMap) {
        GraphQLObjectType objectType = target.objectType;
        List<GraphQLNamedOutputType> resolvedInterfaces = new ArrayList<>();
        for (GraphQLNamedOutputType iface : objectType.getInterfaces()) {
            if (iface instanceof GraphQLTypeReference) {
                String typeName = ((GraphQLTypeReference) iface).getName();
                GraphQLNamedType resolved = typeMap.get(typeName);
                if (resolved == null) {
                    throw new AssertException(String.format("Type '%s' not found in schema", typeName));
                }
                if (!(resolved instanceof GraphQLInterfaceType)) {
                    throw new AssertException(String.format("Type '%s' is not an interface type", typeName));
                }
                resolvedInterfaces.add((GraphQLInterfaceType) resolved);
            } else {
                resolvedInterfaces.add(iface);
            }
        }
        objectType.replaceInterfaces(resolvedInterfaces);
    }

    private void replaceInterfaceInterfaces(InterfaceInterfaceReplaceTarget target, Map<String, GraphQLNamedType> typeMap) {
        GraphQLInterfaceType interfaceType = target.interfaceType;
        List<GraphQLNamedOutputType> resolvedInterfaces = new ArrayList<>();
        for (GraphQLNamedOutputType iface : interfaceType.getInterfaces()) {
            if (iface instanceof GraphQLTypeReference) {
                String typeName = ((GraphQLTypeReference) iface).getName();
                GraphQLNamedType resolved = typeMap.get(typeName);
                if (resolved == null) {
                    throw new AssertException(String.format("Type '%s' not found in schema", typeName));
                }
                if (!(resolved instanceof GraphQLInterfaceType)) {
                    throw new AssertException(String.format("Type '%s' is not an interface type", typeName));
                }
                resolvedInterfaces.add((GraphQLInterfaceType) resolved);
            } else {
                resolvedInterfaces.add(iface);
            }
        }
        interfaceType.replaceInterfaces(resolvedInterfaces);
    }

    private void replaceUnionTypes(UnionTypesReplaceTarget target, Map<String, GraphQLNamedType> typeMap) {
        GraphQLUnionType unionType = target.unionType;
        List<GraphQLNamedOutputType> resolvedTypes = new ArrayList<>();
        for (GraphQLNamedOutputType possibleType : unionType.getTypes()) {
            if (possibleType instanceof GraphQLTypeReference) {
                String typeName = ((GraphQLTypeReference) possibleType).getName();
                GraphQLNamedType resolved = typeMap.get(typeName);
                if (resolved == null) {
                    throw new AssertException(String.format("Type '%s' not found in schema", typeName));
                }
                if (!(resolved instanceof GraphQLObjectType)) {
                    throw new AssertException(String.format("Type '%s' is not an object type (union members must be object types)", typeName));
                }
                resolvedTypes.add((GraphQLObjectType) resolved);
            } else {
                resolvedTypes.add(possibleType);
            }
        }
        unionType.replaceTypes(resolvedTypes);
    }

    /**
     * Resolve an output type, replacing any type references with actual types.
     * Handles List and NonNull wrappers recursively.
     */
    private GraphQLOutputType resolveOutputType(GraphQLOutputType type, Map<String, GraphQLNamedType> typeMap) {
        if (type instanceof GraphQLNonNull) {
            GraphQLNonNull nonNull = (GraphQLNonNull) type;
            GraphQLType wrappedType = nonNull.getWrappedType();
            if (wrappedType instanceof GraphQLOutputType) {
                GraphQLOutputType resolvedWrapped = resolveOutputType((GraphQLOutputType) wrappedType, typeMap);
                if (resolvedWrapped != wrappedType) {
                    nonNull.replaceType(resolvedWrapped);
                }
            }
            return type;
        }
        if (type instanceof GraphQLList) {
            GraphQLList list = (GraphQLList) type;
            GraphQLType wrappedType = list.getWrappedType();
            if (wrappedType instanceof GraphQLOutputType) {
                GraphQLOutputType resolvedWrapped = resolveOutputType((GraphQLOutputType) wrappedType, typeMap);
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
            if (!(resolved instanceof GraphQLOutputType)) {
                throw new AssertException(String.format("Type '%s' is not an output type", typeName));
            }
            return (GraphQLOutputType) resolved;
        }
        // Already a concrete type, return as-is
        return type;
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

    /**
     * Returns an immutable map of interface names to sorted lists of implementing object type names.
     * The object type names are maintained in sorted order as they're added.
     *
     * @return immutable map from interface name to list of object type names
     */
    public ImmutableMap<String, ImmutableList<String>> getInterfaceNameToObjectTypeNames() {
        ImmutableMap.Builder<String, ImmutableList<String>> builder = ImmutableMap.builder();
        for (Map.Entry<String, TreeSet<String>> entry : interfaceToObjectTypeNames.entrySet()) {
            builder.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
        }
        return builder.build();
    }
}
