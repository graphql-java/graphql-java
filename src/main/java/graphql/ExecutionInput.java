package graphql;

import org.dataloader.DataLoaderRegistry;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;

/**
 * This represents the series of values that can be input on a graphql query execution
 */
@PublicApi
public class ExecutionInput {
    private final String query;
    private final List<String> operationNames;
    private final Object context;
    private final Object root;
    private final Map<String, Object> variables;
    private final DataLoaderRegistry dataLoaderRegistry;

    public ExecutionInput(String query, String operationName, Object context, Object root, Map<String, Object> variables) {
        this(query, Collections.singletonList(operationName), context, root, variables, new DataLoaderRegistry());
    }

    @Internal
    private ExecutionInput(String query, List<String> operationNames, Object context, Object root, Map<String, Object> variables, DataLoaderRegistry dataLoaderRegistry) {
        this.query = query;
        this.operationNames = operationNames;
        this.context = context;
        this.root = root;
        this.variables = variables;
        this.dataLoaderRegistry = dataLoaderRegistry;
    }

    /**
     * @return the query text
     */
    public String getQuery() {
        return query;
    }

    /**
     * @return the context object to pass to all data fetchers
     */
    public Object getContext() {
        return context;
    }

    /**
     * @return the root object to start the query execution on
     */
    public Object getRoot() {
        return root;
    }

    /**
     * @return a map of variables that can be referenced via $syntax in the query
     */
    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * @return the data loader registry associated with this execution
     */
    public DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry;
    }

    /**
     * This helps you transform the current ExecutionInput object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new ExecutionInput object based on calling build on that builder
     */
    public ExecutionInput transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder()
                .query(this.query)
                .operationNames(this.operationNames)
                .context(this.context)
                .root(this.root)
                .dataLoaderRegistry(this.dataLoaderRegistry)
                .variables(this.variables);

        builderConsumer.accept(builder);

        return builder.build();
    }

    public <T> Stream<T> mapOperations(Function<String, T> consumer) {
        if (operationNames == null) {
            return Stream.of(consumer.apply(null));
        } else {
            return operationNames.stream().map(consumer);
        }
    }

    @Override
    public String toString() {
        return "ExecutionInput{" +
                "query='" + query + '\'' +
                ", operationNames='" + operationNames + '\'' +
                ", context=" + context +
                ", root=" + root +
                ", variables=" + variables +
                ", dataLoaderRegistry=" + dataLoaderRegistry +
                '}';
    }

    /**
     * @return a new builder of ExecutionInput objects
     */
    public static Builder newExecutionInput() {
        return new Builder();
    }

    public static class Builder {

        private String query;
        private List<String> operationNames;
        private Object context = GraphQLContext.newContext();
        private Object root;
        private Map<String, Object> variables = Collections.emptyMap();
        private DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationNames = Collections.singletonList(operationName);
            return this;
        }

        public Builder operationNames(String ... operationNames) {
            return operationNames(Arrays.asList(operationNames));
        }

        public Builder operationNames(List<String> operationNames) {
            this.operationNames = operationNames;
            return this;
        }

        /**
         * By default you will get a {@link GraphQLContext} object but you can set your own.
         *
         * @param context the context object to use
         *
         * @return this builder
         */
        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        public Builder context(GraphQLContext.Builder contextBuilder) {
            this.context = contextBuilder.build();
            return this;
        }

        public Builder context(UnaryOperator<GraphQLContext.Builder> contextBuilderFunction) {
            GraphQLContext.Builder builder = GraphQLContext.newContext();
            builder = contextBuilderFunction.apply(builder);
            return context(builder.build());
        }

        public Builder root(Object root) {
            this.root = root;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        /**
         * You should create new {@link org.dataloader.DataLoaderRegistry}s and new {@link org.dataloader.DataLoader}s for each execution.  Do not re-use
         * instances as this will create unexpected results.
         *
         * @param dataLoaderRegistry a registry of {@link org.dataloader.DataLoader}s
         *
         * @return this builder
         */
        public Builder dataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry) {
            this.dataLoaderRegistry = assertNotNull(dataLoaderRegistry);
            return this;
        }

        public ExecutionInput build() {
            return new ExecutionInput(query, operationNames, context, root, variables, dataLoaderRegistry);
        }
    }
}
