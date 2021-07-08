package graphql;

import graphql.cachecontrol.CacheControl;
import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationState;
import org.dataloader.DataLoaderRegistry;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;

/**
 * This represents the series of values that can be input on a graphql query execution
 */
@PublicApi
public class ExecutionInput {
    private final String query;
    private final String operationName;
    private final Object context;
    private final GraphQLContext graphQLContext;
    private final Object localContext;
    private final Object root;
    private final Map<String, Object> variables;
    private final Map<String, Object> extensions;
    private final DataLoaderRegistry dataLoaderRegistry;
    private final CacheControl cacheControl;
    private final ExecutionId executionId;
    private final Locale locale;


    @Internal
    private ExecutionInput(Builder builder) {
        this.query = assertNotNull(builder.query, () -> "query can't be null");
        this.operationName = builder.operationName;
        this.context = builder.context;
        this.graphQLContext = assertNotNull(builder.graphQLContext);
        this.root = builder.root;
        this.variables = builder.variables;
        this.dataLoaderRegistry = builder.dataLoaderRegistry;
        this.cacheControl = builder.cacheControl;
        this.executionId = builder.executionId;
        this.locale = builder.locale;
        this.localContext = builder.localContext;
        this.extensions = builder.extensions;
    }

    /**
     * @return the query text
     */
    public String getQuery() {
        return query;
    }

    /**
     * @return the name of the query operation
     */
    public String getOperationName() {
        return operationName;
    }

    /**
     * The legacy context object has been deprecated in favour of the more shareable
     * {@link #getGraphQLContext()}
     *
     * @return the context object to pass to all data fetchers
     *
     * @deprecated - use {@link #getGraphQLContext()}
     */
    @Deprecated
    public Object getContext() {
        return context;
    }

    /**
     * @return the shared {@link GraphQLContext} object to pass to all data fetchers
     */
    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    /**
     * @return the local context object to pass to all top level (i.e. query, mutation, subscription) data fetchers
     */
    public Object getLocalContext() {
        return localContext;
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
     * @return the cache control helper associated with this execution
     */
    public CacheControl getCacheControl() {
        return cacheControl;
    }

    /**
     * @return Id that will be/was used to execute this operation.
     */
    public ExecutionId getExecutionId() {
        return executionId;
    }

    /**
     * This returns the locale of this operation.
     *
     * @return the locale of this operation
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * @return a map of extension values that can be sent in to a request
     */
    public Map<String, Object> getExtensions() {
        return extensions;
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
                .operationName(this.operationName)
                .context(this.context)
                .transfer(this.graphQLContext)
                .localContext(this.localContext)
                .root(this.root)
                .dataLoaderRegistry(this.dataLoaderRegistry)
                .cacheControl(this.cacheControl)
                .variables(this.variables)
                .extensions(this.extensions)
                .executionId(this.executionId)
                .locale(this.locale);

        builderConsumer.accept(builder);

        return builder.build();
    }

    @Override
    public String toString() {
        return "ExecutionInput{" +
                "query='" + query + '\'' +
                ", operationName='" + operationName + '\'' +
                ", context=" + context +
                ", graphQLContext=" + graphQLContext +
                ", root=" + root +
                ", variables=" + variables +
                ", dataLoaderRegistry=" + dataLoaderRegistry +
                ", executionId= " + executionId +
                ", locale= " + locale +
                '}';
    }

    /**
     * @return a new builder of ExecutionInput objects
     */
    public static Builder newExecutionInput() {
        return new Builder();
    }

    /**
     * Creates a new builder of ExecutionInput objects with the given query
     *
     * @param query the query to execute
     *
     * @return a new builder of ExecutionInput objects
     */
    public static Builder newExecutionInput(String query) {
        return new Builder().query(query);
    }

    public static class Builder {

        private String query;
        private String operationName;
        private GraphQLContext graphQLContext = GraphQLContext.newContext().build();
        private Object context = graphQLContext; // we make these the same object on purpose - legacy code will get the same object if this change nothing
        private Object localContext;
        private Object root;
        private Map<String, Object> variables = Collections.emptyMap();
        public Map<String, Object> extensions = Collections.emptyMap();
        //
        // this is important - it allows code to later known if we never really set a dataloader and hence it can optimize
        // dataloader field tracking away.
        //
        private DataLoaderRegistry dataLoaderRegistry = DataLoaderDispatcherInstrumentationState.EMPTY_DATALOADER_REGISTRY;
        private CacheControl cacheControl = CacheControl.newCacheControl();
        private Locale locale;
        private ExecutionId executionId;

        public Builder query(String query) {
            this.query = assertNotNull(query, () -> "query can't be null");
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        /**
         * A default one will be assigned, but you can set your own.
         *
         * @param executionId an execution id object
         *
         * @return this builder
         */
        public Builder executionId(ExecutionId executionId) {
            this.executionId = executionId;
            return this;
        }


        /**
         * Sets the locale to use for this operation
         *
         * @param locale the locale to use
         *
         * @return this builder
         */
        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        /**
         * Sets initial localContext in root data fetchers
         *
         * @param localContext the local context to use
         *
         * @return this builder
         */
        public Builder localContext(Object localContext) {
            this.localContext = localContext;
            return this;
        }

        /**
         * The legacy context object
         *
         * @param context the context object to use
         *
         * @return this builder
         *
         * @deprecated - the {@link ExecutionInput#getGraphQLContext()} is a fixed mutable instance now
         */
        @Deprecated
        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        /**
         * The legacy context object
         *
         * @param contextBuilder the context builder object to use
         *
         * @return this builder
         *
         * @deprecated - the {@link ExecutionInput#getGraphQLContext()} is a fixed mutable instance now
         */
        @Deprecated
        public Builder context(GraphQLContext.Builder contextBuilder) {
            this.context = contextBuilder.build();
            return this;
        }

        /**
         * The legacy context object
         *
         * @param contextBuilderFunction the context builder function to use
         *
         * @return this builder
         *
         * @deprecated - the {@link ExecutionInput#getGraphQLContext()} is a fixed mutable instance now
         */
        @Deprecated
        public Builder context(UnaryOperator<GraphQLContext.Builder> contextBuilderFunction) {
            GraphQLContext.Builder builder = GraphQLContext.newContext();
            builder = contextBuilderFunction.apply(builder);
            return context(builder.build());
        }

        /**
         * This will give you a builder of {@link GraphQLContext} and any values you set will be copied
         * into the underlying {@link GraphQLContext} of this execution input
         *
         * @param builderFunction a builder function you can use to put values into the context
         *
         * @return this builder
         */
        public Builder graphQLContext(Consumer<GraphQLContext.Builder> builderFunction) {
            GraphQLContext.Builder builder = GraphQLContext.newContext();
            builderFunction.accept(builder);
            this.graphQLContext.putAll(builder);
            return this;
        }

        /**
         * This will put all the values from the map into the underlying {@link GraphQLContext} of this execution input
         *
         * @param mapOfContext a map of values to put in the context
         *
         * @return this builder
         */
        public Builder graphQLContext(Map<?, Object> mapOfContext) {
            this.graphQLContext.putAll(mapOfContext);
            return this;
        }

        // hidden on purpose
        private Builder transfer(GraphQLContext graphQLContext) {
            this.graphQLContext = Assert.assertNotNull(graphQLContext);
            return this;
        }

        public Builder root(Object root) {
            this.root = root;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = assertNotNull(variables, () -> "variables map can't be null");
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions = assertNotNull(extensions, () -> "extensions map can't be null");
            return this;
        }

        /**
         * You should create new {@link org.dataloader.DataLoaderRegistry}s and new {@link org.dataloader.DataLoader}s for each execution.  Do not
         * re-use
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

        public Builder cacheControl(CacheControl cacheControl) {
            this.cacheControl = assertNotNull(cacheControl);
            return this;
        }

        public ExecutionInput build() {
            return new ExecutionInput(this);
        }
    }
}