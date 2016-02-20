package graphql.schema;


import graphql.GraphQLException;
import graphql.introspection.Introspection;

import java.util.*;

public class SchemaUtil {

    public boolean isLeafType(GraphQLType type) {
        GraphQLUnmodifiedType unmodifiedType = getUnmodifiedType(type);
        return
                unmodifiedType instanceof GraphQLScalarType
                        || unmodifiedType instanceof GraphQLEnumType;
    }

    public boolean isInputType(GraphQLType graphQLType) {
        GraphQLUnmodifiedType unmodifiedType = getUnmodifiedType(graphQLType);
        return
                unmodifiedType instanceof GraphQLScalarType
                        || unmodifiedType instanceof GraphQLEnumType
                        || unmodifiedType instanceof GraphQLInputObjectType;
    }

    public GraphQLUnmodifiedType getUnmodifiedType(GraphQLType graphQLType) {
        if (graphQLType instanceof GraphQLModifiedType) {
            return getUnmodifiedType(((GraphQLModifiedType) graphQLType).getWrappedType());
        }
        return (GraphQLUnmodifiedType) graphQLType;
    }


    private void collectTypes(GraphQLType root, Map<String, GraphQLType> result) {
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
            collectTypesForInputObjects((GraphQLInputObjectType) root, result);
        } else if (root instanceof GraphQLTypeReference) {
            // nothing to do
        } else {
            throw new RuntimeException("Unknown type " + root);
        }
    }

    private void collectTypesForUnions(GraphQLUnionType unionType, Map<String, GraphQLType> result) {
        result.put(unionType.getName(), unionType);
        for (GraphQLType type : unionType.getTypes()) {
            collectTypes(type, result);
        }

    }

    private void collectTypesForInterfaces(GraphQLInterfaceType interfaceType, Map<String, GraphQLType> result) {
        if (result.containsKey(interfaceType.getName())) return;
        result.put(interfaceType.getName(), interfaceType);

        for (GraphQLFieldDefinition fieldDefinition : interfaceType.getFieldDefinitions()) {
            collectTypes(fieldDefinition.getType(), result);
            for (GraphQLArgument fieldArgument : fieldDefinition.getArguments()) {
                collectTypes(fieldArgument.getType(), result);
            }
        }
    }


    private void collectTypesForObjects(GraphQLObjectType objectType, Map<String, GraphQLType> result) {
        if (result.containsKey(objectType.getName())) return;
        result.put(objectType.getName(), objectType);

        for (GraphQLFieldDefinition fieldDefinition : objectType.getFieldDefinitions()) {
            collectTypes(fieldDefinition.getType(), result);
            for (GraphQLArgument fieldArgument : fieldDefinition.getArguments()) {
                collectTypes(fieldArgument.getType(), result);
            }
        }
        for (GraphQLInterfaceType interfaceType : objectType.getInterfaces()) {
            collectTypes(interfaceType, result);
        }
    }

    private void collectTypesForInputObjects(GraphQLInputObjectType objectType, Map<String, GraphQLType> result) {
        if (result.containsKey(objectType.getName())) return;
        result.put(objectType.getName(), objectType);

        for (GraphQLInputObjectField fieldDefinition : objectType.getFields()) {
            collectTypes(fieldDefinition.getType(), result);
        }
    }


    public Map<String, GraphQLType> allTypes(GraphQLSchema schema, Set<GraphQLType> dictionary) {
        Map<String, GraphQLType> typesByName = new LinkedHashMap<String, GraphQLType>();
        collectTypes(schema.getQueryType(), typesByName);
        if (schema.isSupportingMutations()) {
            collectTypes(schema.getMutationType(), typesByName);
        }
        if (dictionary != null) {
            for (GraphQLType type : dictionary) {
                collectTypes(type, typesByName);
            }
        }
        collectTypes(Introspection.__Schema, typesByName);
        return typesByName;
    }

    public List<GraphQLObjectType> findImplementations(GraphQLSchema schema, GraphQLInterfaceType interfaceType) {
        Map<String, GraphQLType> allTypes = allTypes(schema, schema.getDictionary());
        List<GraphQLObjectType> result = new ArrayList<GraphQLObjectType>();
        for (GraphQLType type : allTypes.values()) {
            if (!(type instanceof GraphQLObjectType)) {
                continue;
            }
            GraphQLObjectType objectType = (GraphQLObjectType) type;
            if ((objectType).getInterfaces().contains(interfaceType)) result.add(objectType);
        }
        return result;
    }


    void replaceTypeReferences(GraphQLSchema schema) {
        Map<String, GraphQLType> typeMap = allTypes(schema, schema.getDictionary());
        for (GraphQLType type : typeMap.values()) {
            if (type instanceof GraphQLFieldsContainer) {
                resolveTypeReferencesForFieldsContainer((GraphQLFieldsContainer) type, typeMap);
            }
        }
    }

    private void resolveTypeReferencesForFieldsContainer(GraphQLFieldsContainer fieldsContainer, Map<String, GraphQLType> typeMap) {
        for (GraphQLFieldDefinition fieldDefinition : fieldsContainer.getFieldDefinitions()) {
            fieldDefinition.replaceTypeReferences(typeMap);
        }
    }

    GraphQLType resolveTypeReference(GraphQLType type, Map<String, GraphQLType> typeMap) {
        if (type instanceof GraphQLTypeReference) {
            GraphQLType resolvedType = typeMap.get(type.getName());
            if (resolvedType == null) {
                throw new GraphQLException("type " + type.getName() + " not found in schema");
            }
            return resolvedType;
        }
        if (type instanceof GraphQLList) {
            ((GraphQLList) type).replaceTypeReferences(typeMap);
        }
        if (type instanceof GraphQLNonNull) {
            ((GraphQLNonNull) type).replaceTypeReferences(typeMap);
        }
        return type;
    }

    List<GraphQLType> resolveTypeReferences(List<GraphQLType> types, Map<String, GraphQLType> typeMap) {
        List<GraphQLType> resolvedTypes = new ArrayList<GraphQLType>();
        for (GraphQLType type : types) {
            resolvedTypes.add(resolveTypeReference(type, typeMap));
        }
        return resolvedTypes;
    }
}
