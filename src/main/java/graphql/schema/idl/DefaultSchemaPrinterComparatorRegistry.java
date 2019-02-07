package graphql.schema.idl;

import graphql.PublicApi;
import graphql.schema.GraphQLType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.schema.idl.SchemaPrinterComparatorEnvironment.newEnvironment;

/**
 * Associates a {@code Comparator} with a {@code SchemaPrinterComparatorEnvironment} to control the scope in which the {@code Comparator} can be applied.
 */
@PublicApi
public class DefaultSchemaPrinterComparatorRegistry implements SchemaPrinterComparatorRegistry {

    public static final Comparator<GraphQLType> DEFAULT_COMPARATOR = Comparator.comparing(GraphQLType::getName);

    private Map<SchemaPrinterComparatorEnvironment, Comparator<?>> registry = new HashMap<>();

    private DefaultSchemaPrinterComparatorRegistry() {
    }

    private DefaultSchemaPrinterComparatorRegistry(Map<SchemaPrinterComparatorEnvironment, Comparator<?>> registry) {
        this.registry = registry;
    }

    /**
     * Search for the most to least specific registered {@code Comparator} otherwise a default is returned.
     */
    @Override
    public <T extends GraphQLType> Comparator<? super T> getComparator(SchemaPrinterComparatorEnvironment environment) {
        Comparator<?> comparator = registry.get(environment);
        if (comparator != null) {
            //noinspection unchecked
            return (Comparator<? super T>) comparator;
        }
        comparator = registry.get(environment.transform(builder -> builder.parentType(null)));
        if (comparator != null) {
            //noinspection unchecked
            return (Comparator<? super T>) comparator;
        }
        return DEFAULT_COMPARATOR;
    }

    /**
     * @return A registry where all {@code GraphQLType}s receive a default {@code Comparator} by comparing {@code GraphQLType::getName}.
     */
    public static DefaultSchemaPrinterComparatorRegistry defaultComparators() {
        return new DefaultSchemaPrinterComparatorRegistry();
    }

    public static Builder newComparators() {
        return new Builder();
    }

    public static class Builder {

        private Map<SchemaPrinterComparatorEnvironment, Comparator<?>> registry = new HashMap<>();

        /**
         * Registers a {@code Comparator} with an environment to control its permitted scope of operation.
         *
         * @param environment     Defines the scope to control where the {@code Comparator} can be applied.
         * @param comparatorClass The {@code Comparator} class for added type safety. It should match {@code environment.elementType}.
         * @param comparator      The {@code Comparator} of type {@code comparatorClass}.
         * @param <T>             The specific {@code GraphQLType} the {@code Comparator} should operate on.
         *
         * @return The {@code Builder} instance to allow chaining.
         */
        public <T extends GraphQLType> Builder addComparator(SchemaPrinterComparatorEnvironment environment, Class<T> comparatorClass, Comparator<? super T> comparator) {
            assertNotNull(environment, "environment can't be null");
            assertNotNull(comparatorClass, "comparatorClass can't be null");
            assertNotNull(comparator, "comparator can't be null");
            registry.put(environment, comparator);
            return this;
        }

        /**
         * Convenience method which supplies an environment builder function.
         *
         * @param builderFunction the function which is given a builder
         * @param comparatorClass The {@code Comparator} class for added type safety. It should match {@code environment.elementType}.
         * @param comparator      The {@code Comparator} of type {@code comparatorClass}.
         * @param <T>             the graphql type
         *
         * @return this builder
         *
         * @see #addComparator
         */
        public <T extends GraphQLType> Builder addComparator(UnaryOperator<SchemaPrinterComparatorEnvironment.Builder> builderFunction,
                                                             Class<T> comparatorClass, Comparator<? super T> comparator) {
            assertNotNull(builderFunction, "builderFunction can't be null");

            SchemaPrinterComparatorEnvironment environment = builderFunction.apply(newEnvironment()).build();
            return addComparator(environment, comparatorClass, comparator);
        }

        public DefaultSchemaPrinterComparatorRegistry build() {
            return new DefaultSchemaPrinterComparatorRegistry(registry);
        }
    }
}
