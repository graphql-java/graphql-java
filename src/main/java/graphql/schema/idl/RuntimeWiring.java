package graphql.schema.idl;

import graphql.PublicApi;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

/**
 * A runtime wiring is a specification of data fetchers, type resolves and custom scalars that are needed
 * to wire together a functional {@link GraphQLSchema}
 */
@PublicApi
public class RuntimeWiring {

    private final Map<String, Map<String, DataFetcher>> dataFetchers;
    private final Map<String, DataFetcher> defaultDataFetchers;
    private final Map<String, GraphQLScalarType> scalars;
    private final Map<String, TypeResolver> typeResolvers;
    private final Map<String, SchemaDirectiveWiring> directiveWiring;
    private final WiringFactory wiringFactory;
    private final Map<String, EnumValuesProvider> enumValuesProviders;
    private final GraphqlFieldVisibility fieldVisibility;

    private RuntimeWiring(Map<String, Map<String, DataFetcher>> dataFetchers, Map<String, DataFetcher> defaultDataFetchers, Map<String, GraphQLScalarType> scalars, Map<String, TypeResolver> typeResolvers, Map<String, SchemaDirectiveWiring> directiveWiring, Map<String, EnumValuesProvider> enumValuesProviders, WiringFactory wiringFactory, GraphqlFieldVisibility fieldVisibility) {
        this.dataFetchers = dataFetchers;
        this.defaultDataFetchers = defaultDataFetchers;
        this.scalars = scalars;
        this.typeResolvers = typeResolvers;
        this.directiveWiring = directiveWiring;
        this.wiringFactory = wiringFactory;
        this.enumValuesProviders = enumValuesProviders;
        this.fieldVisibility = fieldVisibility;
    }

    /**
     * @return a builder of Runtime Wiring
     */
    public static Builder newRuntimeWiring() {
        return new Builder();
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

    public DataFetcher getDefaultDataFetcherForType(String typeName) {
        return defaultDataFetchers.get(typeName);
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

    public GraphqlFieldVisibility getFieldVisibility() {
        return fieldVisibility;
    }

    public Map<String, SchemaDirectiveWiring> getDirectiveWiring() {
        return directiveWiring;
    }

    @PublicApi
    public static class Builder {
        private final Map<String, Map<String, DataFetcher>> dataFetchers = new LinkedHashMap<>();
        private final Map<String, DataFetcher> defaultDataFetchers = new LinkedHashMap<>();
        private final Map<String, GraphQLScalarType> scalars = new LinkedHashMap<>();
        private final Map<String, TypeResolver> typeResolvers = new LinkedHashMap<>();
        private final Map<String, EnumValuesProvider> enumValuesProviders = new LinkedHashMap<>();
        private final Map<String, SchemaDirectiveWiring> directiveWiring = new LinkedHashMap<>();
        private WiringFactory wiringFactory = new NoopWiringFactory();
        private GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;

        private Builder() {
            ScalarInfo.STANDARD_SCALARS.forEach(this::scalar);
            // we give this out by default
            directiveWiring.put(FetchSchemaDirectiveWiring.FETCH, new FetchSchemaDirectiveWiring());
        }

        /**
         * Adds a wiring factory into the runtime wiring
         *
         * @param wiringFactory the wiring factory to add
         *
         * @return this outer builder
         */
        public Builder wiringFactory(WiringFactory wiringFactory) {
            assertNotNull(wiringFactory, "You must provide a wiring factory");
            this.wiringFactory = wiringFactory;
            return this;
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
         * This allows you to add a field visibility that will be associated with the schema
         *
         * @param fieldVisibility the new field visibility
         *
         * @return the runtime wiring builder
         */
        public Builder fieldVisibility(GraphqlFieldVisibility fieldVisibility) {
            this.fieldVisibility = assertNotNull(fieldVisibility);
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
         * @param typeName        the name of the type to wire
         * @param builderFunction a function that will be given the builder to use
         *
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
         *
         * @return the runtime wiring builder
         */
        public Builder type(TypeRuntimeWiring typeRuntimeWiring) {
            String typeName = typeRuntimeWiring.getTypeName();
            Map<String, DataFetcher> typeDataFetchers = dataFetchers.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
            typeRuntimeWiring.getFieldDataFetchers().forEach(typeDataFetchers::put);

            defaultDataFetchers.put(typeName, typeRuntimeWiring.getDefaultDataFetcher());

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
         * This provides the wiring code for a named directive.
         *
         * @param directiveName         the name of the directive to wire
         * @param schemaDirectiveWiring the runtime behaviour of this wiring
         *
         * @return the runtime wiring builder
         *
         * @see graphql.schema.idl.SchemaDirectiveWiring
         */
        public Builder directive(String directiveName, SchemaDirectiveWiring schemaDirectiveWiring) {
            directiveWiring.put(directiveName, schemaDirectiveWiring);
            return this;
        }

        /**
         * @return the built runtime wiring
         */
        public RuntimeWiring build() {
            return new RuntimeWiring(dataFetchers, defaultDataFetchers, scalars, typeResolvers, directiveWiring, enumValuesProviders, wiringFactory, fieldVisibility);
        }

    }


}

