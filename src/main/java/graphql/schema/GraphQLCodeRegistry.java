package graphql.schema;

import graphql.PublicApi;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.schema.DataFetcherFactoryEnvironment.newDataFetchingFactoryEnvironment;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;


@PublicApi
public class GraphQLCodeRegistry {

    private final Map<TypeAndField, DataFetcherFactory> dataFetcherMap;
    private final Map<String, TypeResolver> typeResolverMap;
    private final GraphqlFieldVisibility fieldVisibility;

    private GraphQLCodeRegistry(Map<TypeAndField, DataFetcherFactory> dataFetcherMap, Map<String, TypeResolver> typeResolverMap, GraphqlFieldVisibility fieldVisibility) {
        this.dataFetcherMap = dataFetcherMap;
        this.typeResolverMap = typeResolverMap;
        this.fieldVisibility = fieldVisibility;
    }

    public GraphqlFieldVisibility getFieldVisibility() {
        return fieldVisibility;
    }

    public DataFetcher getDataFetcher(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition) {
        return getDataFetcherImpl(parentType, fieldDefinition, dataFetcherMap);
    }

    private static DataFetcher getDataFetcherImpl(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition, Map<TypeAndField, DataFetcherFactory> dataFetcherMap) {
        assertNotNull(parentType);
        assertNotNull(fieldDefinition);

        DataFetcherFactory dataFetcherFactory = dataFetcherMap.get(mkKey(parentType, fieldDefinition));
        if (dataFetcherFactory == null) {
            // ok lets use the old skool field - later when the field DF is fully removed we
            // will create a property data fetcher but until then we go back to the field def
            // as a back up
            return fieldDefinition.getDataFetcher();
        }
        return dataFetcherFactory.get(newDataFetchingFactoryEnvironment()
                .fieldDefinition(fieldDefinition)
                .build());
    }

    public boolean hasDataFetcher(GraphQLObjectType parentType, GraphQLFieldDefinition fieldDefinition) {
        assertNotNull(parentType);
        assertNotNull(fieldDefinition);
        return dataFetcherMap.containsKey(mkKey(parentType, fieldDefinition));
    }

    public TypeResolver getTypeResolver(GraphQLInterfaceType parentType) {
        return getTypeResolverForInterface(parentType, typeResolverMap);
    }

    public TypeResolver getTypeResolver(GraphQLUnionType parentType) {
        return getTypeResolverForUnion(parentType, typeResolverMap);
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

    private static class TypeAndField {


        private final String typeName;
        private final String fieldName;

        private TypeAndField(String typeName, String fieldName) {
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
            TypeAndField that = (TypeAndField) o;
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
    }

    private static TypeAndField mkKey(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition) {
        return new TypeAndField(parentType.getName(), fieldDefinition.getName());
    }

    private static TypeAndField mkKey(String parentType, GraphQLFieldDefinition fieldDefinition) {
        return new TypeAndField(parentType, fieldDefinition.getName());
    }

    public static Builder newCodeRegistry() {
        return new Builder();
    }

    public GraphQLCodeRegistry transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static class Builder {
        private final Map<TypeAndField, DataFetcherFactory> dataFetcherMap = new HashMap<>();
        private final Map<String, TypeResolver> typeResolverMap = new HashMap<>();
        private GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;


        private Builder() {
        }

        private Builder(GraphQLCodeRegistry codeRegistry) {
            this.dataFetcherMap.putAll(codeRegistry.dataFetcherMap);
            this.typeResolverMap.putAll(codeRegistry.typeResolverMap);
            this.fieldVisibility = codeRegistry.fieldVisibility;
        }

        public DataFetcher getDataFetcher(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition) {
            return getDataFetcherImpl(parentType, fieldDefinition, dataFetcherMap);
        }

        public TypeResolver getTypeResolver(GraphQLInterfaceType parentType) {
            return getTypeResolverForInterface(parentType, typeResolverMap);
        }

        public TypeResolver getTypeResolver(GraphQLUnionType parentType) {
            return getTypeResolverForUnion(parentType, typeResolverMap);
        }

        public Builder dataFetcher(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition, DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher);
            return dataFetcher(parentType, fieldDefinition, DataFetcherFactories.useDataFetcher(dataFetcher));
        }

        public Builder dataFetcher(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition, DataFetcherFactory<?> dataFetcherFactory) {
            assertNotNull(dataFetcherFactory);
            dataFetcherMap.put(mkKey(parentType, fieldDefinition), dataFetcherFactory);
            return this;
        }

        public Builder dataFetcher(String parentTypeName, GraphQLFieldDefinition fieldDefinition, DataFetcherFactory<?> dataFetcherFactory) {
            assertNotNull(dataFetcherFactory);
            dataFetcherMap.put(mkKey(assertValidName(parentTypeName), fieldDefinition), dataFetcherFactory);
            return this;
        }

        public Builder typeResolver(GraphQLInterfaceType parentType, TypeResolver typeResolver) {
            typeResolverMap.put(parentType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolver(GraphQLUnionType parentType, TypeResolver typeResolver) {
            typeResolverMap.put(parentType.getName(), typeResolver);
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
            return new GraphQLCodeRegistry(dataFetcherMap, typeResolverMap, fieldVisibility);
        }

    }
}
