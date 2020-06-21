package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.Type;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.schema.idl.errors.UnionTypeError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * A support class to help break up the large SchemaTypeChecker class. This handles
 * the checking of {@link graphql.language.UnionTypeDefinition}s.
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
                .forEach(type -> checkUnionTypes(errors, typeRegistry, type));
    }

    private void checkUnionTypes(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, UnionTypeDefinition type) {
        List<Type> memberTypes = type.getMemberTypes();

        if (memberTypes == null || memberTypes.size() == 0) {
            errors.add(new UnionTypeError(type, format("Union type \"%s\" must include one or more unique member types.", type.getName())));
        }
        System.out.println(memberTypes);
    }

}
