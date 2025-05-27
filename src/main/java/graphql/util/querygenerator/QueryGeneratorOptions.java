package graphql.util.querygenerator;

import graphql.ExperimentalApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;

import java.util.function.Predicate;

/**
 * Options for the {@link QueryGenerator} class.
 */
@ExperimentalApi
public class QueryGeneratorOptions {
    private final int maxFieldCount;
    private final Predicate<GraphQLFieldsContainer> filterFieldContainerPredicate;
    private final Predicate<GraphQLFieldDefinition> filterFieldDefinitionPredicate;

    private static final int MAX_FIELD_COUNT_LIMIT = 10_000;

    private QueryGeneratorOptions(
            int maxFieldCount,
            Predicate<GraphQLFieldsContainer> filterFieldContainerPredicate,
            Predicate<GraphQLFieldDefinition> filterFieldDefinitionPredicate
    ) {
        this.maxFieldCount = maxFieldCount;
        this.filterFieldContainerPredicate = filterFieldContainerPredicate;
        this.filterFieldDefinitionPredicate = filterFieldDefinitionPredicate;
    }

    /**
     * Returns the maximum number of fields that can be included in the generated query.
     *
     * @return the maximum field count
     */
    public int getMaxFieldCount() {
        return maxFieldCount;
    }

    /**
     * Returns the predicate used to filter field containers.
     * <p>
     * The field container will be filtered out if this predicate returns false.
     *
     * @return the predicate for filtering field containers
     */
    public Predicate<GraphQLFieldsContainer> getFilterFieldContainerPredicate() {
        return filterFieldContainerPredicate;
    }

    /**
     * Returns the predicate used to filter field definitions.
     * <p>
     * The field definition will be filtered out if this predicate returns false.
     *
     * @return the predicate for filtering field definitions
     */
    public Predicate<GraphQLFieldDefinition> getFilterFieldDefinitionPredicate() {
        return filterFieldDefinitionPredicate;
    }

    /**
     * Builder for {@link QueryGeneratorOptions}.
     */
    @ExperimentalApi
    public static class QueryGeneratorOptionsBuilder {
        private int maxFieldCount = MAX_FIELD_COUNT_LIMIT;

        private static final Predicate<?> ALWAYS_TRUE = fieldsContainer -> true;

        @SuppressWarnings("unchecked")
        private static <T> Predicate<T> alwaysTrue() {
            return (Predicate<T>) ALWAYS_TRUE;
        }

        private Predicate<GraphQLFieldsContainer> filterFieldContainerPredicate = alwaysTrue();
        private Predicate<GraphQLFieldDefinition> filterFieldDefinitionPredicate = alwaysTrue();

        private QueryGeneratorOptionsBuilder() {}

        /**
         * Sets the maximum number of fields that can be included in the generated query.
         * <p>
         * This value must be non-negative and cannot exceed {@link #MAX_FIELD_COUNT_LIMIT}.
         *
         * @param maxFieldCount the maximum field count
         * @return this builder
         */
        public QueryGeneratorOptionsBuilder maxFieldCount(int maxFieldCount) {
            if (maxFieldCount < 0) {
                throw new IllegalArgumentException("Max field count cannot be negative");
            }
            if (maxFieldCount > MAX_FIELD_COUNT_LIMIT) {
                throw new IllegalArgumentException("Max field count cannot exceed " + MAX_FIELD_COUNT_LIMIT);
            }
            this.maxFieldCount = maxFieldCount;
            return this;
        }

        /**
         * Sets the predicate used to filter field containers.
         * <p>
         * The field container will be filtered out if this predicate returns false.
         *
         * @param predicate the predicate for filtering field containers
         * @return this builder
         */
        public QueryGeneratorOptionsBuilder filterFieldContainerPredicate(Predicate<GraphQLFieldsContainer> predicate) {
            this.filterFieldContainerPredicate = predicate;
            return this;
        }

        /**
         * Sets the predicate used to filter field definitions.
         * <p>
         * The field definition will be filtered out if this predicate returns false.
         *
         * @param predicate the predicate for filtering field definitions
         * @return this builder
         */
        public QueryGeneratorOptionsBuilder filterFieldDefinitionPredicate(Predicate<GraphQLFieldDefinition> predicate) {
            this.filterFieldDefinitionPredicate = predicate;
            return this;
        }

        public QueryGeneratorOptions build() {
            return new QueryGeneratorOptions(
                    maxFieldCount,
                    filterFieldContainerPredicate,
                    filterFieldDefinitionPredicate
            );
        }
    }

    /**
     * Creates a new {@link QueryGeneratorOptionsBuilder} with default values.
     *
     * @return a new builder instance
     */
    public static QueryGeneratorOptions.QueryGeneratorOptionsBuilder newBuilder() {
        return new QueryGeneratorOptions.QueryGeneratorOptionsBuilder();
    }
}
