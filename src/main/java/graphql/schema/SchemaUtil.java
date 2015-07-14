package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchemaUtil {

    public static boolean isLeafType(GraphQLType type) {
        GraphQLUnmodifiedType unmodifiedType = getUnmodifiedType(type);
        return
                unmodifiedType instanceof GraphQLScalarType ||
                        unmodifiedType instanceof GraphQLEnumType;
    }

    public static GraphQLUnmodifiedType getUnmodifiedType(GraphQLType graphQLType) {
        if (graphQLType instanceof GraphQLModifiedType) {
            return getUnmodifiedType(((GraphQLModifiedType) graphQLType).getWrappedType());
        }
        return (GraphQLUnmodifiedType) graphQLType;
    }


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
            result.put(((GraphQLInputObjectType) root).getName(), root);
        } else if (root instanceof GraphQLTypeReference) {
            // nothing to do
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
            for (GraphQLArgument fieldArgument : fieldDefinition.getArguments()) {
                collectTypes(fieldArgument.getType(), result);
            }
        }
    }


    private static void collectTypesForObjects(GraphQLObjectType objectType, Map<String, GraphQLType> result) {
        if (result.containsKey(objectType.getName())) return;
        result.put(objectType.getName(), objectType);

        for (GraphQLFieldDefinition fieldDefinition : objectType.getFieldDefinitions()) {
            collectTypes(fieldDefinition.getType(), result);
            for (GraphQLArgument fieldArgument : fieldDefinition.getArguments()) {
                collectTypes(fieldArgument.getType(), result);
            }
        }
    }

    public static GraphQLType findType(GraphQLSchema schema, String name) {
        Map<String, GraphQLType> typesByName = allTypes(schema);
        return typesByName.get(name);
    }

    public static Map<String, GraphQLType> allTypes(GraphQLSchema schema) {
        Map<String, GraphQLType> typesByName = new LinkedHashMap<>();
        collectTypes(schema.getQueryType(), typesByName);
        if (schema.isSupportingMutations()) {
            collectTypes(schema.getMutationType(), typesByName);
        }
        return typesByName;
    }

    public static List<GraphQLType> allTypesAsList(GraphQLSchema graphQLSchema) {
        return new ArrayList<>(allTypes(graphQLSchema).values());
    }

    public static List<GraphQLObjectType> findImplementations(GraphQLSchema schema, GraphQLInterfaceType interfaceType) {
        Map<String, GraphQLType> allTypes = allTypes(schema);
        List<GraphQLObjectType> result = new ArrayList<>();
        for (GraphQLType type : allTypes.values()) {
            if (!(type instanceof GraphQLObjectType)) {
                continue;
            }
            GraphQLObjectType objectType = (GraphQLObjectType) type;
            if ((objectType).getInterfaces().contains(interfaceType)) result.add(objectType);
        }
        return result;
    }


    static void replaceTypeReferences(GraphQLSchema schema) {
        Map<String, GraphQLType> typeMap = allTypes(schema);
        for (GraphQLType type : typeMap.values()) {
            if (type instanceof GraphQLFieldsContainer) {
                resolveTypeReferencesForFieldsContainer((GraphQLFieldsContainer) type, typeMap);
            }
        }
    }

    private static void resolveTypeReferencesForFieldsContainer(GraphQLFieldsContainer fieldsContainer, Map<String, GraphQLType> typeMap) {
        for (GraphQLFieldDefinition fieldDefinition : fieldsContainer.getFieldDefinitions()) {
            fieldDefinition.replaceTypeReferences(typeMap);
        }
    }

    static GraphQLType resolveTypeReference(GraphQLType type, Map<String, GraphQLType> typeMap) {
        if (type instanceof GraphQLTypeReference) {
            return typeMap.get(type.getName());
        }
        if (type instanceof GraphQLList) {
            ((GraphQLList) type).replaceTypeReferences(typeMap);
        }
        if (type instanceof GraphQLNonNull) {
            ((GraphQLNonNull) type).replaceTypeReferences(typeMap);
        }
        return type;
    }

    static List<GraphQLType> resolveTypeReferences(List<GraphQLType> types, Map<String, GraphQLType> typeMap) {
        List<GraphQLType> resolvedTypes = new ArrayList<>();
        for (GraphQLType type : types) {
            resolvedTypes.add(resolveTypeReference(type, typeMap));
        }
        return resolvedTypes;
    }
}
