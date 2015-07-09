package graphql.schema;


import java.util.LinkedHashMap;
import java.util.Map;

public class SchemaUtil {


    private static void collectTypes(GraphQLType root, Map<String, GraphQLType> result) {
        if (root instanceof GraphQLNonNull) {
            collectTypes(((GraphQLNonNull) root).getWrappedType(), result);
        } else if (root instanceof GraphQLList) {
            collectTypes(((GraphQLList) root).getWrappedType(), result);
        } else if (root instanceof GraphQLEnumType) {
            result.put(((GraphQLEnumType) root).getName(), root);
        } else if (root instanceof GraphQLScalarType) {
            result.put(((GraphQLScalarType) root).getName(), root);
        } else if (root instanceof GraphQLObjectType) {
            collectTypesForObjects((GraphQLObjectType) root, result);
        } else if (root instanceof GraphQLInterfaceType) {
            collectTypesForInterfaces((GraphQLInterfaceType) root, result);
        } else if (root instanceof GraphQLUnionType) {
            collectTypesForUnions((GraphQLUnionType) root, result);
        } else if (root instanceof GraphQLInputObjectType) {
            result.put(((GraphQLInputObjectType) root).getName(),root);
        } else {
            throw new RuntimeException("Unknown type " + root);
        }
    }

    private static void collectTypesForUnions(GraphQLUnionType unionType, Map<String, GraphQLType> result) {
        for (GraphQLType type : unionType.getTypes()) {
            collectTypes(type, result);
        }

    }

    private static void collectTypesForInterfaces(GraphQLInterfaceType interfaceType, Map<String, GraphQLType> result) {
        if (result.containsKey(interfaceType.getName())) return;
        result.put(interfaceType.getName(), interfaceType);

        for (GraphQLFieldDefinition fieldDefinition : interfaceType.getFieldDefinitions()) {
            collectTypes(fieldDefinition.getType(), result);
            for (GraphQLFieldArgument fieldArgument : fieldDefinition.getArguments()) {
                collectTypes(fieldArgument.getType(), result);
            }
        }
    }


    private static void collectTypesForObjects(GraphQLObjectType objectType, Map<String, GraphQLType> result) {
        if (result.containsKey(objectType.getName())) return;
        result.put(objectType.getName(), objectType);

        for (GraphQLFieldDefinition fieldDefinition : objectType.getFieldDefinitions()) {
            collectTypes(fieldDefinition.getType(), result);
            for (GraphQLFieldArgument fieldArgument : fieldDefinition.getArguments()) {
                collectTypes(fieldArgument.getType(), result);
            }
        }
    }

    //TODO: Move into a better place where it doesn't need to 'cache' it
    public static GraphQLType findType(GraphQLSchema schema, String name) {
        Map<String, GraphQLType> typesByName = new LinkedHashMap<>();
        collectTypes(schema.getQueryType(), typesByName);
        if (schema.isSupportingMutations()) {
            collectTypes(schema.getMutationType(), typesByName);
        }
        return typesByName.get(name);
    }
}
