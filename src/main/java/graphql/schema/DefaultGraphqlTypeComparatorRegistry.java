package graphql.schema;

import com.google.common.collect.ImmutableMap;
import graphql.PublicApi;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphqlTypeComparatorEnvironment.newEnvironment;

/**
 * Associates a {@code Comparator} with a {@code GraphqlTypeComparatorEnvironment} to control the scope in which the {@code Comparator} can be applied.
 */
@PublicApi
public class DefaultGraphqlTypeComparatorRegistry implements GraphqlTypeComparatorRegistry {

    // This sensible order was taken from the original SchemaPrinter code.  It ordered the types in this manner
    private static final ImmutableMap<Class<? extends GraphQLSchemaElement>, Integer> SENSIBLE_ORDER =
            ImmutableMap.<Class<? extends GraphQLSchemaElement>, Integer>builder()
                    .put(GraphQLDirective.class, 1)
                    .put(GraphQLInterfaceType.class, 2)
                    .put(GraphQLUnionType.class, 3)
                    .put(GraphQLObjectType.class, 4)
                    .put(GraphQLEnumType.class, 5)
                    .put(GraphQLScalarType.class, 6)
                    .put(GraphQLInputObjectType.class, 7)
                    .build();

    /**
     * This orders the schema into a sensible grouped order
     * @return a comparator that allows for sensible grouped order
     */
    public static Comparator<GraphQLSchemaElement> sensibleGroupedOrder() {
        return (o1, o2) -> {
            o1 = unwrapElement(o1);
            o2 = unwrapElement(o2);
            int i1 = SENSIBLE_ORDER.getOrDefault(o1.getClass(), 0);
            int i2 = SENSIBLE_ORDER.getOrDefault(o2.getClass(), 0);
            int rc = i1 - i2;
            if (rc == 0) {
                rc = compareByName(o1, o2);
            }
            return rc;
        };
    }

    private static GraphQLSchemaElement unwrapElement(GraphQLSchemaElement element) {
        if (element instanceof GraphQLType) {
            element = unwrapAll((GraphQLType) element);
        }
        return element;
    }

    private static int compareByName(GraphQLSchemaElement o1, GraphQLSchemaElement o2) {
        return Comparator.comparing(element -> {
            if (element instanceof GraphQLType) {
                element = unwrapAll((GraphQLType) element);
            }
            if (element instanceof GraphQLNamedSchemaElement) {
                return ((GraphQLNamedSchemaElement) element).getName();
            } else {
                return Objects.toString(element);
            }
        }).compare(o1, o2);
    }

    public static final Comparator<GraphQLSchemaElement> DEFAULT_COMPARATOR = sensibleGroupedOrder();

    private Map<GraphqlTypeComparatorEnvironment, Comparator<?>> registry = new HashMap<>();

    private DefaultGraphqlTypeComparatorRegistry() {
    }

    private DefaultGraphqlTypeComparatorRegistry(Map<GraphqlTypeComparatorEnvironment, Comparator<?>> registry) {
        this.registry = registry;
    }

    /**
     * Search for the most to least specific registered {@code Comparator} otherwise a default is returned.
     */
    @Override
    public <T extends GraphQLSchemaElement> Comparator<? super T> getComparator(GraphqlTypeComparatorEnvironment environment) {
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
    public static GraphqlTypeComparatorRegistry defaultComparators() {
        return new DefaultGraphqlTypeComparatorRegistry();
    }

    public static Builder newComparators() {
        return new Builder();
    }

    public static class Builder {

        private final Map<GraphqlTypeComparatorEnvironment, Comparator<?>> registry = new HashMap<>();

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
        public <T extends GraphQLType> Builder addComparator(GraphqlTypeComparatorEnvironment environment, Class<T> comparatorClass, Comparator<? super T> comparator) {
            assertNotNull(environment, () -> "environment can't be null");
            assertNotNull(comparatorClass, () -> "comparatorClass can't be null");
            assertNotNull(comparator, () -> "comparator can't be null");
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
        public <T extends GraphQLType> Builder addComparator(UnaryOperator<GraphqlTypeComparatorEnvironment.Builder> builderFunction,
                                                             Class<T> comparatorClass, Comparator<? super T> comparator) {
            assertNotNull(builderFunction, () -> "builderFunction can't be null");

            GraphqlTypeComparatorEnvironment environment = builderFunction.apply(newEnvironment()).build();
            return addComparator(environment, comparatorClass, comparator);
        }

        public DefaultGraphqlTypeComparatorRegistry build() {
            return new DefaultGraphqlTypeComparatorRegistry(registry);
        }
    }
}
