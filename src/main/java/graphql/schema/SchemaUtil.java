package graphql.schema;


import graphql.Assert;
import graphql.AssertException;
import graphql.Internal;
import graphql.introspection.Introspection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static java.lang.String.format;

@Internal
public class SchemaUtil {

    @SuppressWarnings("StatementWithEmptyBody")
    private void collectTypes(GraphQLType root, Map<String, GraphQLType> result) {
        if (isNonNull(root)) {
            collectTypes(unwrapOne(root), result);
        } else if (isList(root)) {
            collectTypes(unwrapOne(root), result);
        } else if (root instanceof GraphQLEnumType) {
            assertTypeUniqueness(root, result);
            result.put(root.getName(), root);
        } else if (root instanceof GraphQLScalarType) {
            assertTypeUniqueness(root, result);
            result.put(root.getName(), root);
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
            Assert.assertShouldNeverHappen("Unknown type %s", root);
        }
    }

    /*
        From http://facebook.github.io/graphql/#sec-Type-System

           All types within a GraphQL schema must have unique names. No two provided types may have the same name.
           No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).

        Enforcing this helps avoid problems later down the track fo example https://github.com/graphql-java/graphql-java/issues/373
     */
    private void assertTypeUniqueness(GraphQLType type, Map<String, GraphQLType> result) {
        GraphQLType existingType = result.get(type.getName());
        // do we have an existing definition
        if (existingType != null) {
            // type references are ok
            if (!(existingType instanceof GraphQLTypeReference || type instanceof GraphQLTypeReference))
                // object comparison here is deliberate
                if (existingType != type) {
                    throw new AssertException(format("All types within a GraphQL schema must have unique names. No two provided types may have the same name.\n" +
                                    "No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).\n" +
                                    "You have redefined the type '%s' from being a '%s' to a '%s'",
                            type.getName(), existingType.getClass().getSimpleName(), type.getClass().getSimpleName()));
                }
        }
    }

    private void collectTypesForUnions(GraphQLUnionType unionType, Map<String, GraphQLType> result) {
        assertTypeUniqueness(unionType, result);

        result.put(unionType.getName(), unionType);
        for (GraphQLType type : unionType.getTypes()) {
            collectTypes(type, result);
        }

    }

    private void collectTypesForInterfaces(GraphQLInterfaceType interfaceType, Map<String, GraphQLType> result) {
        if (result.containsKey(interfaceType.getName()) && !(result.get(interfaceType.getName()) instanceof GraphQLTypeReference)) {
            assertTypeUniqueness(interfaceType, result);
            return;
        }
        result.put(interfaceType.getName(), interfaceType);

        // this deliberately has open field visibility as its collecting on the whole schema
        for (GraphQLFieldDefinition fieldDefinition : interfaceType.getFieldDefinitions()) {
            collectTypes(fieldDefinition.getType(), result);
            for (GraphQLArgument fieldArgument : fieldDefinition.getArguments()) {
                collectTypes(fieldArgument.getType(), result);
            }
        }
    }


    private void collectTypesForObjects(GraphQLObjectType objectType, Map<String, GraphQLType> result) {
        if (result.containsKey(objectType.getName()) && !(result.get(objectType.getName()) instanceof GraphQLTypeReference)) {
            assertTypeUniqueness(objectType, result);
            return;
        }
        result.put(objectType.getName(), objectType);

        // this deliberately has open field visibility as its collecting on the whole schema
        for (GraphQLFieldDefinition fieldDefinition : objectType.getFieldDefinitions()) {
            collectTypes(fieldDefinition.getType(), result);
            for (GraphQLArgument fieldArgument : fieldDefinition.getArguments()) {
                collectTypes(fieldArgument.getType(), result);
            }
        }
        for (GraphQLOutputType interfaceType : objectType.getInterfaces()) {
            collectTypes(interfaceType, result);
        }
    }

    private void collectTypesForInputObjects(GraphQLInputObjectType objectType, Map<String, GraphQLType> result) {
        if (result.containsKey(objectType.getName()) && !(result.get(objectType.getName()) instanceof GraphQLTypeReference)) {
            assertTypeUniqueness(objectType, result);
            return;
        }
        result.put(objectType.getName(), objectType);

        for (GraphQLInputObjectField fieldDefinition : objectType.getFields()) {
            collectTypes(fieldDefinition.getType(), result);
        }
    }


    Map<String, GraphQLType> allTypes(GraphQLSchema schema, Set<GraphQLType> additionalTypes) {
        Map<String, GraphQLType> typesByName = new LinkedHashMap<>();
        collectTypes(schema.getQueryType(), typesByName);
        if (schema.isSupportingMutations()) {
            collectTypes(schema.getMutationType(), typesByName);
        }
        if (schema.isSupportingSubscriptions()) {
            collectTypes(schema.getSubscriptionType(), typesByName);
        }
        if (additionalTypes != null) {
            for (GraphQLType type : additionalTypes) {
                collectTypes(type, typesByName);
            }
        }
        collectTypes(Introspection.__Schema, typesByName);
        return typesByName;
    }

    /*
     * Indexes GraphQLObject types registered with the provided schema by implemented GraphQLInterface name
     *
     * This helps in accelerates/simplifies collecting types that implement a certain interface
     *
     * Provided to replace {@link #findImplementations(graphql.schema.GraphQLSchema, graphql.schema.GraphQLInterfaceType)}
     * 
     */
    Map<String, List<GraphQLObjectType>> groupImplementations(GraphQLSchema schema) {
        Map<String, List<GraphQLObjectType>> result = new HashMap<>();
        for (GraphQLType type : schema.getAllTypesAsList()) {
            if (type instanceof GraphQLObjectType) {
                for (GraphQLOutputType interfaceType : ((GraphQLObjectType) type).getInterfaces()) {
                    List<GraphQLObjectType> myGroup = result.computeIfAbsent(interfaceType.getName(), k -> new ArrayList<>());
                    myGroup.add((GraphQLObjectType) type);
                }
            }
        }

        return result;
    }

    /**
     * This method is deprecated due to a performance concern.
     *
     * The Algorithm complexity: O(n^2), where n is number of registered GraphQLTypes
     *
     * That indexing operation is performed twice per input document:
     * 1. during validation
     * 2. during execution
     *
     * We now indexed all types at the schema creation, which has brought complexity down to O(1)
     *
     * @param schema        GraphQL schema
     * @param interfaceType an interface type to find implementations for
     *
     * @return List of object types implementing provided interface
     *
     * @deprecated use {@link graphql.schema.GraphQLSchema#getImplementations(GraphQLInterfaceType)} instead
     */
    @Deprecated
    public List<GraphQLObjectType> findImplementations(GraphQLSchema schema, GraphQLInterfaceType interfaceType) {
        List<GraphQLObjectType> result = new ArrayList<>();
        for (GraphQLType type : schema.getAllTypesAsList()) {
            if (!(type instanceof GraphQLObjectType)) {
                continue;
            }
            GraphQLObjectType objectType = (GraphQLObjectType) type;
            if ((objectType).getInterfaces().contains(interfaceType)) {
                result.add(objectType);
            }
        }
        return result;
    }


    void replaceTypeReferences(GraphQLSchema schema) {
        Map<String, GraphQLType> typeMap = schema.getTypeMap();
        for (GraphQLType type : typeMap.values()) {
            if (type instanceof GraphQLFieldsContainer) {
                resolveTypeReferencesForFieldsContainer((GraphQLFieldsContainer) type, typeMap);
            }
            if (type instanceof GraphQLInputFieldsContainer) {
                resolveTypeReferencesForInputFieldsContainer((GraphQLInputFieldsContainer) type, typeMap);
            }
            if (type instanceof GraphQLObjectType) {
                ((GraphQLObjectType) type).replaceTypeReferences(typeMap);
            }
            if (type instanceof GraphQLUnionType) {
                ((GraphQLUnionType) type).replaceTypeReferences(typeMap);
            }
        }
    }

    private void resolveTypeReferencesForFieldsContainer(GraphQLFieldsContainer fieldsContainer, Map<String, GraphQLType> typeMap) {
        for (GraphQLFieldDefinition fieldDefinition : fieldsContainer.getFieldDefinitions()) {
            fieldDefinition.replaceTypeReferences(typeMap);
            for (GraphQLArgument argument : fieldDefinition.getArguments()) {
                argument.replaceTypeReferences(typeMap);
            }
        }
    }

    private void resolveTypeReferencesForInputFieldsContainer(GraphQLInputFieldsContainer fieldsContainer, Map<String, GraphQLType> typeMap) {
        for (GraphQLInputObjectField fieldDefinition : fieldsContainer.getFieldDefinitions()) {
            fieldDefinition.replaceTypeReferences(typeMap);
        }
    }

    GraphQLType resolveTypeReference(GraphQLType type, Map<String, GraphQLType> typeMap) {
        if (type instanceof GraphQLTypeReference || typeMap.containsKey(type.getName())) {
            GraphQLType resolvedType = typeMap.get(type.getName());
            Assert.assertTrue(resolvedType != null, "type %s not found in schema", type.getName());
            return resolvedType;
        }
        if (isList(type)) {
            ((GraphQLList) type).replaceTypeReferences(typeMap);
        }
        if (isNonNull(type)) {
            ((GraphQLNonNull) type).replaceTypeReferences(typeMap);
        }
        return type;
    }
}
