package graphql.schema;

import graphql.PublicApi;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.Assert.assertValidName;
import static graphql.schema.DataFetcherFactoryEnvironment.newDataFetchingFactoryEnvironment;
import static graphql.schema.GraphQLCodeRegistry.TypeAndFieldKey.mkKey;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;


/**
 * The {@link graphql.schema.GraphQLCodeRegistry} holds that execution code that is associated with graphql types, namely
 * the {@link graphql.schema.DataFetcher}s associated with fields, the {@link graphql.schema.TypeResolver}s associated with
 * abstract types and the {@link graphql.schema.visibility.GraphqlFieldVisibility}
 *
 * For legacy reasons these code functions can still exist on the original type objects but this will be removed in a future version.  Once
 * removed the type system objects will be able have proper hashCode/equals methods and be checked for proper equality.
 */
@PublicApi
public class GraphQLCodeRegistry {

    private final Map<TypeAndFieldKey, DataFetcherFactory> dataFetcherMap;
    private final Map<String, DataFetcherFactory> systemDataFetcherMap;
    private final Map<String, TypeResolver> typeResolverMap;
    private final GraphqlFieldVisibility fieldVisibility;

    private GraphQLCodeRegistry(Map<TypeAndFieldKey, DataFetcherFactory> dataFetcherMap, Map<String, DataFetcherFactory> systemDataFetcherMap, Map<String, TypeResolver> typeResolverMap, GraphqlFieldVisibility fieldVisibility) {
        this.dataFetcherMap = dataFetcherMap;
        this.systemDataFetcherMap = systemDataFetcherMap;
        this.typeResolverMap = typeResolverMap;
        this.fieldVisibility = fieldVisibility;
    }

    /**
     * @return the {@link graphql.schema.visibility.GraphqlFieldVisibility}
     */
    public GraphqlFieldVisibility getFieldVisibility() {
        return fieldVisibility;
    }

    /**
     * Returns a data fetcher associated with a field within a container type
     *
     * @param parentType      the container type
     * @param fieldDefinition the field definition
     *
     * @return the DataFetcher associated with this field.  All fields have data fetchers
     */
    public DataFetcher getDataFetcher(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition) {
        return getDataFetcherImpl(parentType, fieldDefinition, dataFetcherMap, systemDataFetcherMap);
    }

    private static DataFetcher getDataFetcherImpl(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition, Map<TypeAndFieldKey, DataFetcherFactory> dataFetcherMap, Map<String, DataFetcherFactory> systemDataFetcherMap) {
        assertNotNull(parentType);
        assertNotNull(fieldDefinition);

        DataFetcherFactory dataFetcherFactory = systemDataFetcherMap.get(fieldDefinition.getName());
        if (dataFetcherFactory == null) {
            dataFetcherFactory = dataFetcherMap.get(mkKey(parentType, fieldDefinition));
            if (dataFetcherFactory == null) {
                dataFetcherFactory = DataFetcherFactories.useDataFetcher(new PropertyDataFetcher<>(fieldDefinition.getName()));
            }
        }
        return dataFetcherFactory.get(newDataFetchingFactoryEnvironment()
                .fieldDefinition(fieldDefinition)
                .build());
    }

    private static boolean hasDataFetcherImpl(String parentTypeName, String fieldName, Map<TypeAndFieldKey, DataFetcherFactory> dataFetcherMap, Map<String, DataFetcherFactory> systemDataFetcherMap) {
        assertNotNull(parentTypeName);
        assertNotNull(fieldName);

        DataFetcherFactory dataFetcherFactory = systemDataFetcherMap.get(fieldName);
        if (dataFetcherFactory == null) {
            dataFetcherFactory = dataFetcherMap.get(mkKey(parentTypeName, fieldName));
        }
        return dataFetcherFactory != null;
    }


    /**
     * Returns the type resolver associated with this interface type
     *
     * @param interfaceType the interface type
     *
     * @return a non null {@link graphql.schema.TypeResolver}
     */
    public TypeResolver getTypeResolver(GraphQLInterfaceType interfaceType) {
        return getTypeResolverForInterface(interfaceType, typeResolverMap);
    }

    /**
     * Returns the type resolver associated with this union type
     *
     * @param unionType the union type
     *
     * @return a non null {@link graphql.schema.TypeResolver}
     */

    public TypeResolver getTypeResolver(GraphQLUnionType unionType) {
        return getTypeResolverForUnion(unionType, typeResolverMap);
    }

    private static TypeResolver getTypeResolverForInterface(GraphQLInterfaceType parentType, Map<String, TypeResolver> typeResolverMap) {
        assertNotNull(parentType);
        TypeResolver typeResolver = typeResolverMap.get(parentType.getName());
        if (typeResolver == null) {
            typeResolver = parentType.getTypeResolver();
        }
        return assertNotNull(typeResolver, "There must be a type resolver for interface " + parentType.getName());
    }

    private static TypeResolver getTypeResolverForUnion(GraphQLUnionType parentType, Map<String, TypeResolver> typeResolverMap) {
        assertNotNull(parentType);
        TypeResolver typeResolver = typeResolverMap.get(parentType.getName());
        if (typeResolver == null) {
            typeResolver = parentType.getTypeResolver();
        }
        return assertNotNull(typeResolver, "There must be a type resolver for union " + parentType.getName());
    }

    public static class TypeAndFieldKey {

        private final String typeName;
        private final String fieldName;

        private TypeAndFieldKey(String typeName, String fieldName) {
            this.typeName = typeName;
            this.fieldName = fieldName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TypeAndFieldKey that = (TypeAndFieldKey) o;
            return Objects.equals(typeName, that.typeName) &&
                    Objects.equals(fieldName, that.fieldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeName, fieldName);
        }

        @Override
        public String toString() {
            return typeName + ':' + fieldName + '\'';
        }

        public static TypeAndFieldKey mkKey(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition) {
            return new TypeAndFieldKey(parentType.getName(), fieldDefinition.getName());
        }

        public static TypeAndFieldKey mkKey(String parentType, String fieldName) {
            return new TypeAndFieldKey(parentType, fieldName);
        }
    }


    /**
     * This helps you transform the current {@link graphql.schema.GraphQLCodeRegistry} object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new GraphQLCodeRegistry object based on calling build on that builder
     */
    public GraphQLCodeRegistry transform(Consumer<Builder> builderConsumer) {
        Builder builder = newCodeRegistry(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * @return a new builder of {@link graphql.schema.GraphQLCodeRegistry} objects
     */
    public static Builder newCodeRegistry() {
        return new Builder();
    }

    /**
     * Returns a new builder of {@link graphql.schema.GraphQLCodeRegistry} objects based on the existing one
     *
     * @param existingCodeRegistry the existing code registry to use
     *
     * @return a new builder of {@link graphql.schema.GraphQLCodeRegistry} objects
     */
    public static Builder newCodeRegistry(GraphQLCodeRegistry existingCodeRegistry) {
        return new Builder(existingCodeRegistry);
    }

    public static class Builder {
        private final Map<TypeAndFieldKey, DataFetcherFactory> dataFetcherMap = new LinkedHashMap<>();
        private final Map<String, DataFetcherFactory> systemDataFetcherMap = new LinkedHashMap<>();
        private final Map<String, TypeResolver> typeResolverMap = new HashMap<>();
        private GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;


        private Builder() {
        }

        private Builder(GraphQLCodeRegistry codeRegistry) {
            this.dataFetcherMap.putAll(codeRegistry.dataFetcherMap);
            this.typeResolverMap.putAll(codeRegistry.typeResolverMap);
            this.fieldVisibility = codeRegistry.fieldVisibility;
        }

        /**
         * Returns a data fetcher associated with a field within a container type
         *
         * @param parentType      the container type
         * @param fieldDefinition the field definition
         *
         * @return the DataFetcher associated with this field.  All fields have data fetchers
         */
        public DataFetcher getDataFetcher(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition) {
            return getDataFetcherImpl(parentType, fieldDefinition, dataFetcherMap, systemDataFetcherMap);
        }

        /**
         * Returns a data fetcher associated with a field within a container type
         *
         * @param parentTypeName the container type name
         * @param fieldName      the field definition name
         *
         * @return the true if there is a data fetcher already for this field
         */
        public boolean hasDataFetcher(String parentTypeName, String fieldName) {
            return hasDataFetcherImpl(parentTypeName, fieldName, dataFetcherMap, systemDataFetcherMap);
        }

        /**
         * Returns the type resolver associated with this interface type
         *
         * @param interfaceType the interface type
         *
         * @return a non null {@link graphql.schema.TypeResolver}
         */
        public TypeResolver getTypeResolver(GraphQLInterfaceType interfaceType) {
            return getTypeResolverForInterface(interfaceType, typeResolverMap);
        }

        /**
         * Returns true of a type resolver has been registered for this type name
         *
         * @param typeName the name to check
         *
         * @return true if there is already a type resolver
         */
        public boolean hasTypeResolver(String typeName) {
            return typeResolverMap.containsKey(typeName);
        }

        /**
         * Returns the type resolver associated with this union type
         *
         * @param unionType the union type
         *
         * @return a non null {@link graphql.schema.TypeResolver}
         */
        public TypeResolver getTypeResolver(GraphQLUnionType unionType) {
            return getTypeResolverForUnion(unionType, typeResolverMap);
        }

        /**
         * Sets the data fetcher for a specific field inside a container type
         *
         * @param parentTypeContainer the parent container type
         * @param fieldDefinition     the field definition
         * @param dataFetcher         the data fetcher code for that field
         *
         * @return this builder
         */
        public Builder dataFetcher(GraphQLFieldsContainer parentTypeContainer, GraphQLFieldDefinition fieldDefinition, DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher);
            return dataFetcher(parentTypeContainer, fieldDefinition, DataFetcherFactories.useDataFetcher(dataFetcher));
        }

        /**
         * Called to place system data fetchers (eg Introspection fields) into the mix
         *
         * @param fieldDefinition the field definition
         * @param dataFetcher     the data fetcher code for that field
         *
         * @return this builder
         */
        public Builder systemDataFetcher(GraphQLFieldDefinition fieldDefinition, DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher);
            assertTrue(fieldDefinition.getName().startsWith("__"), "Only __ fields can be named as system data fetchers");
            systemDataFetcherMap.put(fieldDefinition.getName(), DataFetcherFactories.useDataFetcher(dataFetcher));
            return this;
        }

        /**
         * Sets the data fetcher factory for a specific field inside a container type
         *
         * @param parentTypeContainer the parent container type
         * @param fieldDefinition     the field definition
         * @param dataFetcherFactory  the data fetcher factory code for that field
         *
         * @return this builder
         */
        public Builder dataFetcher(GraphQLFieldsContainer parentTypeContainer, GraphQLFieldDefinition fieldDefinition, DataFetcherFactory<?> dataFetcherFactory) {
            assertNotNull(dataFetcherFactory);
            dataFetcherMap.put(mkKey(parentTypeContainer, fieldDefinition), dataFetcherFactory);
            return this;
        }

        /**
         * Sets the data fetcher factory for a specific field inside a container type ONLY if not mapping has already been made
         *
         * @param parentTypeContainer the parent container type
         * @param fieldDefinition     the field definition
         * @param dataFetcher         the data fetcher code for that field
         *
         * @return this builder
         */
        public Builder dataFetcherIfAbsent(GraphQLFieldsContainer parentTypeContainer, GraphQLFieldDefinition fieldDefinition, DataFetcher<?> dataFetcher) {
            dataFetcherMap.putIfAbsent(mkKey(parentTypeContainer, fieldDefinition), DataFetcherFactories.useDataFetcher(dataFetcher));
            return this;
        }

        /**
         * Sets the data fetcher for a specific field inside a container type
         *
         * @param parentTypeName the parent container type
         * @param fieldName      the field name
         * @param dataFetcher    the data fetcher code for that field
         *
         * @return this builder
         */
        public Builder dataFetcher(String parentTypeName, String fieldName, DataFetcher<?> dataFetcher) {
            return dataFetcher(parentTypeName, fieldName, DataFetcherFactories.useDataFetcher(dataFetcher));
        }

        /**
         * Sets the data fetcher factory for a specific field inside a container type
         *
         * @param parentTypeName     the parent container type
         * @param fieldName          the field name
         * @param dataFetcherFactory the data fetcher factory code for that field
         *
         * @return this builder
         */
        public Builder dataFetcher(String parentTypeName, String fieldName, DataFetcherFactory<?> dataFetcherFactory) {
            assertNotNull(dataFetcherFactory);
            dataFetcherMap.put(mkKey(assertValidName(parentTypeName), assertValidName(fieldName)), dataFetcherFactory);
            return this;
        }

        public Builder dataFetcher(TypeAndFieldKey key, DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher);
            dataFetcherMap.put(key, DataFetcherFactories.useDataFetcher(dataFetcher));
            return this;
        }

        /**
         * This allows you you to build all the data fetchers for the fields of a container type.
         *
         * @param parentTypeContainer the parent container type
         * @param fieldDataFetchers   the map of field names to data fetchers
         *
         * @return this builder
         */
        public Builder dataFetchers(GraphQLFieldsContainer parentTypeContainer, Map<String, DataFetcher> fieldDataFetchers) {
            return dataFetchers(parentTypeContainer.getName(), fieldDataFetchers);
        }

        /**
         * This allows you you to build all the data fetchers for the fields of a container type.
         *
         * @param parentTypeName    the parent container type
         * @param fieldDataFetchers the map of field names to data fetchers
         *
         * @return this builder
         */
        public Builder dataFetchers(String parentTypeName, Map<String, DataFetcher> fieldDataFetchers) {
            assertNotNull(fieldDataFetchers);
            fieldDataFetchers.forEach((fieldName, dataFetcher) -> {
                dataFetcher(parentTypeName, fieldName, dataFetcher);
            });
            return this;
        }

        public Builder typeResolver(GraphQLInterfaceType parentType, TypeResolver typeResolver) {
            typeResolverMap.put(parentType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolverIfAbsent(GraphQLInterfaceType parentType, TypeResolver typeResolver) {
            typeResolverMap.putIfAbsent(parentType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolver(GraphQLUnionType parentType, TypeResolver typeResolver) {
            typeResolverMap.put(parentType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolverIfAbsent(GraphQLUnionType parentType, TypeResolver typeResolver) {
            typeResolverMap.putIfAbsent(parentType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolver(String parentTypeName, TypeResolver typeResolver) {
            typeResolverMap.put(assertValidName(parentTypeName), typeResolver);
            return this;
        }

        public Builder fieldVisibility(GraphqlFieldVisibility fieldVisibility) {
            this.fieldVisibility = assertNotNull(fieldVisibility);
            return this;
        }

        public Builder clearDataFetchers() {
            dataFetcherMap.clear();
            return this;
        }

        public Builder clearTypeResolvers() {
            typeResolverMap.clear();
            return this;
        }

        public GraphQLCodeRegistry build() {
            return new GraphQLCodeRegistry(dataFetcherMap, systemDataFetcherMap, typeResolverMap, fieldVisibility);
        }
    }
}
