package graphql.schema;

import graphql.PublicApi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.schema.DataFetcherFactoryEnvironment.newDataFetchingFactoryEnvironment;


@PublicApi
public class GraphQLCodeRegistry {

    private final Map<TypeAndField, DataFetcherFactory> dataFetcherMap;
    private final Map<String, TypeResolver> typeResolverMap;

    private GraphQLCodeRegistry(Map<TypeAndField, DataFetcherFactory> dataFetcherMap, Map<String, TypeResolver> typeResolverMap) {
        this.dataFetcherMap = dataFetcherMap;
        this.typeResolverMap = typeResolverMap;
    }

    public DataFetcher getDataFetcher(GraphQLObjectType parentType, GraphQLFieldDefinition fieldDefinition) {
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
        assertNotNull(parentType);
        TypeResolver typeResolver = typeResolverMap.get(parentType.getName());
        return assertNotNull(typeResolver, "There must be a type resolver for interface " + parentType.getName());
    }

    public TypeResolver getTypeResolver(GraphQLUnionType parentType) {
        assertNotNull(parentType);
        TypeResolver typeResolver = typeResolverMap.get(parentType.getName());
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
    }

    private static TypeAndField mkKey(GraphQLObjectType parentType, GraphQLFieldDefinition fieldDefinition) {
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


        private Builder() {
        }

        private Builder(GraphQLCodeRegistry codeRegistry) {
            dataFetcherMap.putAll(codeRegistry.dataFetcherMap);
            typeResolverMap.putAll(codeRegistry.typeResolverMap);
        }

        public Builder dataFetcher(GraphQLObjectType parentType, GraphQLFieldDefinition fieldDefinition, DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher);
            return dataFetcher(parentType, fieldDefinition, DataFetcherFactories.useDataFetcher(dataFetcher));
        }

        public Builder dataFetcher(GraphQLObjectType parentType, GraphQLFieldDefinition fieldDefinition, DataFetcherFactory<?> dataFetcherFactory) {
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

        public Builder clearDataFetchers() {
            dataFetcherMap.clear();
            return this;
        }

        public Builder clearTypeResolvers() {
            typeResolverMap.clear();
            return this;
        }

        public GraphQLCodeRegistry build() {
            return new GraphQLCodeRegistry(dataFetcherMap, typeResolverMap);
        }

    }
}
