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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * UnionType check, details in https://spec.graphql.org/June2018/#sec-Type-System.
 * <pre>
 *     <ur>
 *         <li>Union type must include one or more unique member types;</li>
 *         <li>The member types of a Union type must all be Object base types;</li>
 *         <li>Invalid name begin with "__" (two underscores).</li>
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


    void checkUnionTypes(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry) {
        List<UnionTypeDefinition> unionTypes = typeRegistry.getTypes(UnionTypeDefinition.class);
        List<UnionTypeExtensionDefinition> unionTypeExtensions = typeRegistry.getTypes(UnionTypeExtensionDefinition.class);

        Stream.of(unionTypes.stream(), unionTypeExtensions.stream())
                .flatMap(Function.identity())
                .forEach(type -> checkUnionTypes(typeRegistry, type, errors));
    }

    private void checkUnionTypes(TypeDefinitionRegistry typeRegistry, UnionTypeDefinition type, List<GraphQLError> errors) {
        assertTypeName(type, errors);

        List<Type> memberTypes = type.getMemberTypes();
        if (memberTypes == null || memberTypes.size() == 0) {
            errors.add(new UnionTypeError(type, format("Union type \"%s\" must include one or more unique member types.", type.getName())));
        }

        Set<String> typeNames = new HashSet<>();
        for (Type memberType : memberTypes) {
            String memberTypeName = ((TypeName) memberType).getName();
            Optional<TypeDefinition> memberTypeDefinition = typeRegistry.getType(memberTypeName);

            if (!memberTypeDefinition.isPresent() || !(memberTypeDefinition.get() instanceof ObjectTypeDefinition)) {
                errors.add(new UnionTypeError(type, format("The member types of a Union type must all be Object base types. member type \"%s\" in Union \"%s\" is invalid.", ((TypeName) memberType).getName(), type.getName())));
                continue;
            }

            if (typeNames.contains(memberTypeName)) {
                errors.add(new UnionTypeError(type, format("The member types of a Union type must be unique. member type \"%s\" in Union \"%s\" is not unique.", ((TypeName) memberType).getName(), type.getName())));
            }
        }
    }

    private void assertTypeName(UnionTypeDefinition type, List<GraphQLError> errors) {
        if (type.getName().length() >= 2 && type.getName().startsWith("__")) {
            errors.add((new UnionTypeError(type, String.format("\"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", type.getName()))));
        }
    }

}
