package graphql.schema.idl;

import graphql.schema.GraphQLType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;

/**
 * Associates a {@code Comparator} for a specific {@code GraphQLType} together with a {@code SchemaPrinterComparatorEnvironment} to control the scope
 * in which the {@code Comparator} can be applied.
 */
public class DefaultSchemaPrinterComparatorRegistry implements SchemaPrinterComparatorRegistry {

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
        comparator = registry.get(environment.withElementTypeOnly());
        if (comparator != null) {
            //noinspection unchecked
            return (Comparator<? super T>) comparator;
        }
        return Comparator.comparing(GraphQLType::getName);
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
         * Registers a {@code Comparator} with its permitted scope of operation.
         *
         * @param environment Defines the scope to control where the {@code Comparator} can be applied.
         * @param comparatorClass The {@code Comparator} class for added type safety. It should be the same type as {@code environment.elementType}.
         * @param comparator The {@code Comparator} for comparing the {@code comparatorClass} to register for the supplied environment scope.
         * @param <T> The {@code GraphQLType}.
         * @return The {@code Builder} instance to allow chaining.
         */
        public <T extends GraphQLType> Builder add(SchemaPrinterComparatorEnvironment environment, Class<T> comparatorClass, Comparator<? super T> comparator) {
            assertNotNull(environment, "environment can't be null");
            assertNotNull(comparatorClass, "comparatorClass can't be null");
            assertNotNull(comparator, "comparator can't be null");
            registry.put(environment, comparator);
            return this;
        }

        /**
         * Convenience method which supplies an environment builder function.
         * @see #add
         */
        public <T extends GraphQLType> Builder add(UnaryOperator<SchemaPrinterComparatorEnvironment.Builder> builderFunction,
                Class<T> comparatorClass, Comparator<? super T> comparator) {
            assertNotNull(builderFunction, "builderFunction can't be null");
            assertNotNull(comparatorClass, "comparatorClass can't be null");
            assertNotNull(comparator, "comparator can't be null");
            registry.put(builderFunction.apply(SchemaPrinterComparatorEnvironment.newEnvironment()).build(), comparator);
            return this;
        }

        public DefaultSchemaPrinterComparatorRegistry build() {
            return new DefaultSchemaPrinterComparatorRegistry(registry);
        }
    }
}
