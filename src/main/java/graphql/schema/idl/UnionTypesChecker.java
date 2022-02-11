package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.schema.idl.errors.UnionTypeError;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * UnionType check, details in https://spec.graphql.org/June2018/#sec-Type-System.
 * <pre>
 *     <ur>
 *         <li>Invalid name begin with "__" (two underscores);</li>
 *         <li>Union type must include one or more member types;</li>
 *         <li>The member types of a Union type must all be Object base types;</li>
 *         <li>The member types of a Union type must be unique.</li>
 *     </ur>
 * </pre>
 */
@Internal
class UnionTypesChecker {
    private static final Map<Class<? extends UnionTypeDefinition>, String> TYPE_OF_MAP = new HashMap<>();

    static {
        TYPE_OF_MAP.put(UnionTypeDefinition.class, "union");
        TYPE_OF_MAP.put(UnionTypeExtensionDefinition.class, "union extension");
    }


    void checkUnionType(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        List<UnionTypeDefinition> unionTypes = typeRegistry.getTypes(UnionTypeDefinition.class);
        List<UnionTypeExtensionDefinition> unionTypeExtensions = typeRegistry.getTypes(UnionTypeExtensionDefinition.class);

        Stream.concat(unionTypes.stream(), unionTypeExtensions.stream())
                .forEach(type -> checkUnionType(typeRegistry, type, errors));
    }

    private void checkUnionType(TypeDefinitionRegistry typeRegistry, UnionTypeDefinition unionTypeDefinition, List<GraphQLError> errors) {
        assertTypeName(unionTypeDefinition, errors);

        List<Type> memberTypes = unionTypeDefinition.getMemberTypes();
        if (memberTypes == null || memberTypes.size() == 0) {
            errors.add(new UnionTypeError(unionTypeDefinition, format("Union type '%s' must include one or more member types.", unionTypeDefinition.getName())));
            return;
        }

        Set<String> typeNames = new LinkedHashSet<>();
        for (Type memberType : memberTypes) {
            String memberTypeName = ((TypeName) memberType).getName();
            Optional<TypeDefinition> memberTypeDefinition = typeRegistry.getType(memberTypeName);

            if (!memberTypeDefinition.isPresent() || !(memberTypeDefinition.get() instanceof ObjectTypeDefinition)) {
                errors.add(new UnionTypeError(unionTypeDefinition, format("The member types of a Union type must all be Object base types. member type '%s' in Union '%s' is invalid.", ((TypeName) memberType).getName(), unionTypeDefinition.getName())));
                continue;
            }

            if (typeNames.contains(memberTypeName)) {
                errors.add(new UnionTypeError(unionTypeDefinition, format("member type '%s' in Union '%s' is not unique. The member types of a Union type must be unique.", ((TypeName) memberType).getName(), unionTypeDefinition.getName())));
                continue;
            }
            typeNames.add(memberTypeName);
        }
    }

    private void assertTypeName(UnionTypeDefinition unionTypeDefinition, List<GraphQLError> errors) {
        if (unionTypeDefinition.getName().length() >= 2 && unionTypeDefinition.getName().startsWith("__")) {
            errors.add((new UnionTypeError(unionTypeDefinition, String.format("'%s' must not begin with '__', which is reserved by GraphQL introspection.", unionTypeDefinition.getName()))));
        }
    }

}
