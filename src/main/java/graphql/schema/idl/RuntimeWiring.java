package graphql.schema.idl;

import graphql.Assert;
import graphql.PublicApi;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A runtime wiring is a specification of data fetchers, type resolves and custom scalars that are needed
 * to wire together a functional {@link GraphQLSchema}
 */
@PublicApi
public class RuntimeWiring {

    private final Map<String, Map<String, DataFetcher>> dataFetchers;
    private final Map<String, GraphQLScalarType> scalars;
    private final Map<String, TypeResolver> typeResolvers;
    private Map<String, EnumValuesProvider> enumValuesProviders;
    private final WiringFactory wiringFactory;

    private RuntimeWiring(Map<String, Map<String, DataFetcher>> dataFetchers, Map<String, GraphQLScalarType> scalars, Map<String, TypeResolver> typeResolvers, Map<String, EnumValuesProvider> enumValuesProviders, WiringFactory wiringFactory) {
        this.dataFetchers = dataFetchers;
        this.scalars = scalars;
        this.typeResolvers = typeResolvers;
        this.wiringFactory = wiringFactory;
        this.enumValuesProviders = enumValuesProviders;
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

    public Map<String, EnumValuesProvider> getEnumValuesProviders() {
        return this.enumValuesProviders;
    }

    public WiringFactory getWiringFactory() {
        return wiringFactory;
    }

    /**
     * @return a builder of Runtime Wiring
     */
    public static Builder newRuntimeWiring() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private final Map<String, Map<String, DataFetcher>> dataFetchers = new LinkedHashMap<>();
        private final Map<String, GraphQLScalarType> scalars = new LinkedHashMap<>();
        private final Map<String, TypeResolver> typeResolvers = new LinkedHashMap<>();
        private final Map<String, EnumValuesProvider> enumValuesProviders = new LinkedHashMap<>();
        private WiringFactory wiringFactory = new NoopWiringFactory();

        private Builder() {
            ScalarInfo.STANDARD_SCALARS.forEach(this::scalar);
        }

        /**
         * Adds a wiring factory into the runtime wiring
         *
         * @param wiringFactory the wiring factory to add
         * @return this outer builder
         */
        public Builder wiringFactory(WiringFactory wiringFactory) {
            Assert.assertNotNull(wiringFactory, "You must provide a wiring factory");
            this.wiringFactory = wiringFactory;
            return this;
        }

        /**
         * This allows you to add in new custom Scalar implementations beyond the standard set.
         *
         * @param scalarType the new scalar implementation
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
         * @return this outer builder
         */
        public Builder type(TypeRuntimeWiring.Builder builder) {
            return type(builder.build());
        }

        /**
         * This form allows a lambda to be used as the builder of a type wiring
         *
         * @param typeName        the name of the type to wire
         * @param builderFunction a function that will be given the builder to use
         * @return the runtime wiring builder
         */
        public Builder type(String typeName, UnaryOperator<TypeRuntimeWiring.Builder> builderFunction) {
            TypeRuntimeWiring.Builder builder = builderFunction.apply(TypeRuntimeWiring.newTypeWiring(typeName));
            return type(builder.build());
        }

        /**
         * This adds a type wiring
         *
         * @param typeRuntimeWiring the new type wiring
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

            EnumValuesProvider enumValuesProvider = typeRuntimeWiring.getEnumValuesProvider();
            if (enumValuesProvider != null) {
                this.enumValuesProviders.put(typeName, enumValuesProvider);
            }
            return this;
        }

        /**
         * @return the built runtime wiring
         */
        public RuntimeWiring build() {
            return new RuntimeWiring(dataFetchers, scalars, typeResolvers, enumValuesProviders, wiringFactory);
        }

    }


}

