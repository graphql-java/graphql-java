package graphql.schema.idl;

import graphql.PublicApi;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.TypeResolver;
import graphql.schema.idl.errors.StrictModeWiringException;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullUnmarked;

import static graphql.Assert.assertNotNull;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;
import static java.lang.String.format;

/**
 * A runtime wiring is a specification of data fetchers, type resolvers and custom scalars that are needed
 * to wire together a functional {@link GraphQLSchema}
 */
@PublicApi
public class RuntimeWiring {

    private final Map<String, Map<String, DataFetcher>> dataFetchers;
    private final Map<String, DataFetcher> defaultDataFetchers;
    private final Map<String, GraphQLScalarType> scalars;
    private final Map<String, TypeResolver> typeResolvers;
    private final Map<String, SchemaDirectiveWiring> registeredDirectiveWiring;
    private final List<SchemaDirectiveWiring> directiveWiring;
    private final WiringFactory wiringFactory;
    private final Map<String, EnumValuesProvider> enumValuesProviders;
    private final GraphqlFieldVisibility fieldVisibility;
    private final GraphQLCodeRegistry codeRegistry;
    private final GraphqlTypeComparatorRegistry comparatorRegistry;

    /**
     * This is a Runtime wiring which provides mocked types resolver
     * and scalars. It is useful for testing only, for example for creating schemas
     * that can be inspected but not executed on.
     */
    public static final RuntimeWiring MOCKED_WIRING = RuntimeWiring
            .newRuntimeWiring()
            .wiringFactory(new MockedWiringFactory()).build();

    private RuntimeWiring(Builder builder) {
        this.dataFetchers = builder.dataFetchers;
        this.defaultDataFetchers = builder.defaultDataFetchers;
        this.scalars = builder.scalars;
        this.typeResolvers = builder.typeResolvers;
        this.registeredDirectiveWiring = builder.registeredDirectiveWiring;
        this.directiveWiring = builder.directiveWiring;
        this.wiringFactory = builder.wiringFactory;
        this.enumValuesProviders = builder.enumValuesProviders;
        this.fieldVisibility = builder.fieldVisibility;
        this.codeRegistry = builder.codeRegistry;
        this.comparatorRegistry = builder.comparatorRegistry;
    }

    /**
     * @return a builder of Runtime Wiring
     */
    public static Builder newRuntimeWiring() {
        return new Builder();
    }

    /**
     * @param originalRuntimeWiring the runtime wiring to start from
     *
     * @return a builder of Runtime Wiring based on the provided one
     */
    public static Builder newRuntimeWiring(RuntimeWiring originalRuntimeWiring) {
        Builder builder = new Builder();
        builder.dataFetchers.putAll(originalRuntimeWiring.dataFetchers);
        builder.defaultDataFetchers.putAll(originalRuntimeWiring.defaultDataFetchers);
        builder.scalars.putAll(originalRuntimeWiring.scalars);
        builder.typeResolvers.putAll(originalRuntimeWiring.typeResolvers);
        builder.registeredDirectiveWiring.putAll(originalRuntimeWiring.registeredDirectiveWiring);
        builder.directiveWiring.addAll(originalRuntimeWiring.directiveWiring);
        builder.wiringFactory = originalRuntimeWiring.wiringFactory;
        builder.enumValuesProviders.putAll(originalRuntimeWiring.enumValuesProviders);
        builder.fieldVisibility = originalRuntimeWiring.fieldVisibility;
        builder.codeRegistry = originalRuntimeWiring.codeRegistry;
        builder.comparatorRegistry = originalRuntimeWiring.comparatorRegistry;
        return builder;
    }

    /**
     * This helps you transform the current RuntimeWiring object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new RuntimeWiring object based on calling build on that builder
     */
    public RuntimeWiring transform(Consumer<Builder> builderConsumer) {
        Builder builder = newRuntimeWiring(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public GraphQLCodeRegistry getCodeRegistry() {
        return codeRegistry;
    }

    public Map<String, GraphQLScalarType> getScalars() {
        return new LinkedHashMap<>(scalars);
    }

    public Map<String, Map<String, DataFetcher>> getDataFetchers() {
        return dataFetchers;
    }

    /**
     * This is deprecated because the name has the wrong plural case.
     *
     * @param typeName the type for fetch a map of per field data fetchers for
     *
     * @return a map of field data fetchers for a type
     *
     * @deprecated See {@link #getDataFetchersForType(String)}
     */
    @Deprecated(since = "2024-04-28")
    public Map<String, DataFetcher> getDataFetcherForType(String typeName) {
        return dataFetchers.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
    }

    /**
     * This returns a map of the data fetchers per field on that named type.
     *
     * @param typeName the type for fetch a map of per field data fetchers for
     *
     * @return a map of field data fetchers for a type
     */
    public Map<String, DataFetcher> getDataFetchersForType(String typeName) {
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

    public Map<String, SchemaDirectiveWiring> getRegisteredDirectiveWiring() {
        return registeredDirectiveWiring;
    }

    public List<SchemaDirectiveWiring> getDirectiveWiring() {
        return directiveWiring;
    }

    public GraphqlTypeComparatorRegistry getComparatorRegistry() {
        return comparatorRegistry;
    }

    @PublicApi
    @NullUnmarked
    public static class Builder {
        private final Map<String, Map<String, DataFetcher>> dataFetchers = new LinkedHashMap<>();
        private final Map<String, DataFetcher> defaultDataFetchers = new LinkedHashMap<>();
        private final Map<String, GraphQLScalarType> scalars = new LinkedHashMap<>();
        private final Map<String, TypeResolver> typeResolvers = new LinkedHashMap<>();
        private final Map<String, EnumValuesProvider> enumValuesProviders = new LinkedHashMap<>();
        private final Map<String, SchemaDirectiveWiring> registeredDirectiveWiring = new LinkedHashMap<>();
        private final List<SchemaDirectiveWiring> directiveWiring = new ArrayList<>();
        private WiringFactory wiringFactory = new NoopWiringFactory();
        private boolean strictMode = true;
        private GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;
        private GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().build();
        private GraphqlTypeComparatorRegistry comparatorRegistry = GraphqlTypeComparatorRegistry.AS_IS_REGISTRY;

        private Builder() {
            ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS.forEach(this::scalar);
        }

        /**
         * This sets strict mode as true or false. If strictMode is true, if things get defined twice, for example, it will throw a {@link StrictModeWiringException}.
         *
         * @return this builder
         */
        public Builder strictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }

        /**
         * This puts the builder into strict mode, so if things get defined twice, for example, it will throw a {@link StrictModeWiringException}.
         *
         * @return this builder
         *
         * @deprecated strictMode default value changed to true, use {@link #strictMode(boolean)} instead
         */
        @Deprecated(since = "2025-03-22", forRemoval = true)
        public Builder strictMode() {
            this.strictMode = true;
            return this;
        }

        /**
         * Adds a wiring factory into the runtime wiring
         *
         * @param wiringFactory the wiring factory to add
         *
         * @return this outer builder
         */
        public Builder wiringFactory(WiringFactory wiringFactory) {
            assertNotNull(wiringFactory, () -> "You must provide a wiring factory");
            this.wiringFactory = wiringFactory;
            return this;
        }

        /**
         * This allows you to seed in your own {@link graphql.schema.GraphQLCodeRegistry} instance
         *
         * @param codeRegistry the code registry to use
         *
         * @return this outer builder
         */
        public Builder codeRegistry(GraphQLCodeRegistry codeRegistry) {
            this.codeRegistry = assertNotNull(codeRegistry);
            return this;
        }

        /**
         * This allows you to seed in your own {@link graphql.schema.GraphQLCodeRegistry} instance
         *
         * @param codeRegistry the code registry to use
         *
         * @return this outer builder
         */
        public Builder codeRegistry(GraphQLCodeRegistry.Builder codeRegistry) {
            this.codeRegistry = assertNotNull(codeRegistry).build();
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
            if (strictMode && scalars.containsKey(scalarType.getName())) {
                throw new StrictModeWiringException(format("The scalar %s is already defined", scalarType.getName()));
            }
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

            Map<String, DataFetcher> additionalFieldDataFetchers = typeRuntimeWiring.getFieldDataFetchers();
            if (strictMode && !typeDataFetchers.isEmpty()) {
                // Check if the existing type wiring contains overlapping DataFetcher definitions
                for (String fieldName : additionalFieldDataFetchers.keySet()) {
                    if (typeDataFetchers.containsKey(fieldName)) {
                        throw new StrictModeWiringException(format("The field %s on type %s has already been defined", fieldName, typeName));
                    }
                }
            }
            typeDataFetchers.putAll(additionalFieldDataFetchers);

            DataFetcher<?> defaultDataFetcher = typeRuntimeWiring.getDefaultDataFetcher();
            if (defaultDataFetcher != null) {
                if (strictMode && defaultDataFetchers.containsKey(typeName)) {
                    throw new StrictModeWiringException(format("The type %s already has a default data fetcher defined", typeName));
                }
                defaultDataFetchers.put(typeName, defaultDataFetcher);
            }

            TypeResolver typeResolver = typeRuntimeWiring.getTypeResolver();
            if (typeResolver != null) {
                if (strictMode && this.typeResolvers.containsKey(typeName)) {
                    throw new StrictModeWiringException(format("The type %s already has a type resolver defined", typeName));
                }
                this.typeResolvers.put(typeName, typeResolver);
            }

            EnumValuesProvider enumValuesProvider = typeRuntimeWiring.getEnumValuesProvider();
            if (enumValuesProvider != null) {
                if (strictMode && this.enumValuesProviders.containsKey(typeName)) {
                    throw new StrictModeWiringException(format("The type %s already has a enum provider defined", typeName));
                }
                this.enumValuesProviders.put(typeName, enumValuesProvider);
            }
            return this;
        }

        /**
         * This provides the wiring code for a named directive.
         * <p>
         * Note: The provided directive wiring will ONLY be called back if an element has a directive
         * with the specified name.
         * <p>
         * To be called back for every directive the use {@link #directiveWiring(SchemaDirectiveWiring)} or
         * use {@link graphql.schema.idl.WiringFactory#providesSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment)}
         * instead.
         *
         * @param directiveName         the name of the directive to wire
         * @param schemaDirectiveWiring the runtime behaviour of this wiring
         *
         * @return the runtime wiring builder
         *
         * @see #directiveWiring(SchemaDirectiveWiring)
         * @see graphql.schema.idl.SchemaDirectiveWiring
         * @see graphql.schema.idl.WiringFactory#providesSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment)
         */
        public Builder directive(String directiveName, SchemaDirectiveWiring schemaDirectiveWiring) {
            registeredDirectiveWiring.put(directiveName, schemaDirectiveWiring);
            return this;
        }

        /**
         * This adds a directive wiring that will be called for all directives.
         * <p>
         * Note : Unlike {@link #directive(String, SchemaDirectiveWiring)} which is only called back if a  named
         * directives is present, this directive wiring will be called back for every element
         * in the schema even if it has zero directives.
         *
         * @param schemaDirectiveWiring the runtime behaviour of this wiring
         *
         * @return the runtime wiring builder
         *
         * @see #directive(String, SchemaDirectiveWiring)
         * @see graphql.schema.idl.SchemaDirectiveWiring
         * @see graphql.schema.idl.WiringFactory#providesSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment)
         */
        public Builder directiveWiring(SchemaDirectiveWiring schemaDirectiveWiring) {
            directiveWiring.add(schemaDirectiveWiring);
            return this;
        }

        /**
         * You can specify your own sort order of graphql types via {@link graphql.schema.GraphqlTypeComparatorRegistry}
         * which will tell you what type of objects you are to sort when
         * it asks for a comparator.
         *
         * @param comparatorRegistry your own comparator registry
         *
         * @return the runtime wiring builder
         */
        public Builder comparatorRegistry(GraphqlTypeComparatorRegistry comparatorRegistry) {
            this.comparatorRegistry = comparatorRegistry;
            return this;
        }


        /**
         * @return the built runtime wiring
         */
        public RuntimeWiring build() {
            return new RuntimeWiring(this);
        }

    }
}

