package graphql.util.querygenerator;

import com.google.common.base.Predicates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;

import java.util.function.Predicate;

public class QueryGeneratorOptions {
    private final int maxFieldCount;
    private final Predicate<GraphQLFieldsContainer> filterFieldContainerPredicate;
    private final Predicate<GraphQLFieldDefinition> filterFieldDefinitionPredicate;

    private static final int MAX_FIELD_COUNT_LIMIT = 10_000;

    public QueryGeneratorOptions(
            int maxFieldCount,
            Predicate<GraphQLFieldsContainer> filterFieldContainerPredicate,
            Predicate<GraphQLFieldDefinition> filterFieldDefinitionPredicate
    ) {
        this.maxFieldCount = maxFieldCount;
        this.filterFieldContainerPredicate = filterFieldContainerPredicate;
        this.filterFieldDefinitionPredicate = filterFieldDefinitionPredicate;
    }

    public int getMaxFieldCount() {
        return maxFieldCount;
    }

    public Predicate<GraphQLFieldsContainer> getFilterFieldContainerPredicate() {
        return filterFieldContainerPredicate;
    }

    public Predicate<GraphQLFieldDefinition> getFilterFieldDefinitionPredicate() {
        return filterFieldDefinitionPredicate;
    }

    public static class QueryGeneratorOptionsBuilder {
        private int maxFieldCount = MAX_FIELD_COUNT_LIMIT;
        private Predicate<GraphQLFieldsContainer> filterFieldContainerPredicate = Predicates.alwaysTrue();
        private Predicate<GraphQLFieldDefinition> filterFieldDefinitionPredicate = Predicates.alwaysTrue();

        private QueryGeneratorOptionsBuilder() {}

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

        public QueryGeneratorOptionsBuilder filterFieldContainerPredicate(Predicate<GraphQLFieldsContainer> predicate) {
            this.filterFieldContainerPredicate = predicate;
            return this;
        }

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

    public static QueryGeneratorOptions.QueryGeneratorOptionsBuilder builder() {
        return new QueryGeneratorOptions.QueryGeneratorOptionsBuilder();
    }

    public static QueryGeneratorOptions.QueryGeneratorOptionsBuilder newBuilder() {
        return new QueryGeneratorOptions.QueryGeneratorOptionsBuilder();
    }
}
