package graphql.schema.idl;

import graphql.Assert;
import graphql.schema.BuilderFunction;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A runtime wiring is a specification of data fetchers, type resolves and custom scalars that are needed
 * to wire together a functional {@link GraphQLSchema}
 */
public class RuntimeWiring {

    private final Map<String, Map<String, DataFetcher>> dataFetchers;
    private final Map<String, GraphQLScalarType> scalars;
    private final Map<String, TypeResolver> typeResolvers;

    private RuntimeWiring(Map<String, Map<String, DataFetcher>> dataFetchers, Map<String, GraphQLScalarType> scalars, Map<String, TypeResolver> typeResolvers) {
        this.dataFetchers = dataFetchers;
        this.scalars = scalars;
        this.typeResolvers = typeResolvers;
    }

    public Map<String, GraphQLScalarType> getScalars() {
        return new LinkedHashMap<>(scalars);
    }

    public Map<String, Map<String, DataFetcher>> getDataFetchers() {
        return dataFetchers;
    }

    public Map<String, DataFetcher> getDataFetcherForType(String typeName) {
        return dataFetchers.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
    }

    public Map<String, TypeResolver> getTypeResolvers() {
        return typeResolvers;
    }

    /**
     * @return a builder of Runtime Wiring
     */
    public static Builder newRuntimeWiring() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Map<String, DataFetcher>> dataFetchers = new LinkedHashMap<>();
        private final Map<String, GraphQLScalarType> scalars = new LinkedHashMap<>();
        private final Map<String, TypeResolver> typeResolvers = new LinkedHashMap<>();

        private Builder() {
            ScalarInfo.STANDARD_SCALARS.forEach(this::scalar);
        }

        /**
         * This allows you to add in new custom Scalar implementations beyond the standard set.
         *
         * @param scalarType the new scalar implementation
         *
         * @return the runtime wiring builder
         */
        public Builder scalar(GraphQLScalarType scalarType) {
            scalars.put(scalarType.getName(), scalarType);
            return this;
        }

        /**
         * This allows you to add a new type wiring via a builder
         *
         * @param builder the type wiring builder to use
         *
         * @return this outer builder
         */
        public Builder type(TypeRuntimeWiring.Builder builder) {
            return type(builder.build());
        }

        /**
         * This form allows a lambda to be used as the builder of a type wiring
         *
         * @param builderFunction a function that will be given the builder to use
         *
         * @return the runtime wiring builder
         */
        public Builder type(BuilderFunction<TypeRuntimeWiring.Builder> builderFunction) {
            TypeRuntimeWiring.Builder builder = builderFunction.apply(TypeRuntimeWiring.newTypeWiring());
            return type(builder.build());
        }

        /**
         * This adds a type wiring
         *
         * @param typeRuntimeWiring the new type wiring
         *
         * @return the runtime wiring builder
         */
        public Builder type(TypeRuntimeWiring typeRuntimeWiring) {
            String typeName = typeRuntimeWiring.getTypeName();
            Map<String, DataFetcher> typeDataFetchers = dataFetchers.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
            typeRuntimeWiring.getFieldDataFetchers().forEach(typeDataFetchers::put);

            TypeResolver typeResolver = typeRuntimeWiring.getTypeResolver();
            if (typeResolver != null) {
                this.typeResolvers.put(typeName, typeResolver);
            }
            return this;
        }

        /**
         * @return the built runtime wiring
         */
        public RuntimeWiring build() {
            return new RuntimeWiring(dataFetchers, scalars, typeResolvers);
        }

    }


    public static class TypeRuntimeWiring {
        private final String typeName;
        private final Map<String, DataFetcher> fieldDataFetchers;
        private final TypeResolver typeResolver;

        private TypeRuntimeWiring(String typeName, Map<String, DataFetcher> fieldDataFetchers, TypeResolver typeResolver) {
            this.typeName = typeName;
            this.fieldDataFetchers = fieldDataFetchers;
            this.typeResolver = typeResolver;
        }

        public String getTypeName() {
            return typeName;
        }

        public Map<String, DataFetcher> getFieldDataFetchers() {
            return fieldDataFetchers;
        }

        public TypeResolver getTypeResolver() {
            return typeResolver;
        }

        /**
         * Creates a new type wiring builder
         *
         * @param typeName the name of the type to wire
         *
         * @return the builder
         */
        public static Builder newTypeWiring(String typeName) {
            return new Builder().typeName(typeName);
        }

        /**
         * Creates a new type wiring builder
         *
         * @return the builder
         */
        public static Builder newTypeWiring() {
            return new Builder();
        }

        /**
         * This form allows a lambda to be used as the builder
         *
         * @param builderFunction a function that will be given the builder to use
         *
         * @return the same builder back please
         */
        public static TypeRuntimeWiring newTypeWiring(BuilderFunction<TypeRuntimeWiring.Builder> builderFunction) {
            return builderFunction.apply(newTypeWiring()).build();
        }

        public static class Builder {
            private String typeName;
            private final Map<String, DataFetcher> fieldDataFetchers = new LinkedHashMap<>();
            private TypeResolver typeResolver;

            public Builder() {
            }

            /**
             * Sets the type name for this type wiring.  You MUST set this.
             *
             * @param typeName the name of the type
             *
             * @return the current type wiring
             */
            public Builder typeName(String typeName) {
                this.typeName = typeName;
                return this;
            }

            /**
             * Adds a data fetcher for the current type to the specified field
             *
             * @param fieldName   the field that data fetcher should apply to
             * @param dataFetcher the new data Fetcher
             *
             * @return the current type wiring
             */
            public Builder dataFetcher(String fieldName, DataFetcher dataFetcher) {
                Assert.assertNotNull(dataFetcher, "you must provide a data fetcher");
                Assert.assertNotNull(fieldName, "you must tell us what field");
                fieldDataFetchers.put(fieldName, dataFetcher);
                return this;
            }

            /**
             * Adds data fetchers for the current type to the specified field
             *
             * @param dataFetchersMap a map of fields to data fetchers
             *
             * @return the current type wiring
             */
            public Builder dataFetchers(Map<String, DataFetcher> dataFetchersMap) {
                Assert.assertNotNull(dataFetchersMap, "you must provide a data fetchers map");
                fieldDataFetchers.putAll(dataFetchersMap);
                return this;
            }

            /**
             * Adds a {@link TypeResolver} to the current type.  This MUST be specified for Interface
             * and Union types.
             *
             * @param typeResolver the type resolver in play
             *
             * @return the current type wiring
             */
            public Builder typeResolver(TypeResolver typeResolver) {
                Assert.assertNotNull(typeResolver, "you must provide a type resolver");
                this.typeResolver = typeResolver;
                return this;
            }

            /**
             * @return the built type wiring
             */
            public TypeRuntimeWiring build() {
                Assert.assertNotNull(typeName, "you must provide a type name");
                return new TypeRuntimeWiring(typeName, fieldDataFetchers, typeResolver);
            }
        }

    }
}

