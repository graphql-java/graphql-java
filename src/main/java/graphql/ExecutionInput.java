package graphql;

import graphql.collect.ImmutableKit;
import graphql.execution.ExecutionId;
import graphql.execution.RawVariables;
import graphql.execution.preparsed.persisted.PersistedQuerySupport;
import org.dataloader.DataLoaderRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.execution.instrumentation.dataloader.EmptyDataLoaderRegistryInstance.EMPTY_DATALOADER_REGISTRY;

/**
 * This represents the series of values that can be input on a graphql query execution
 */
@PublicApi
@NullMarked
public class ExecutionInput {
    private final String query;
    private final String operationName;
    private final @Nullable Object context;
    private final GraphQLContext graphQLContext;
    private final @Nullable Object localContext;
    private final Object root;
    private final RawVariables rawVariables;
    private final Map<String, Object> extensions;
    private final DataLoaderRegistry dataLoaderRegistry;
    private final ExecutionId executionId;
    private final Locale locale;
    private final AtomicBoolean cancelled;
    private final boolean profileExecution;

    /**
     * In order for {@link #getQuery()} to never be null, use this to mark
     * them so that invariant can be satisfied while assuming that the persisted query
     * id is elsewhere.
     */
    public final static String PERSISTED_QUERY_MARKER = PersistedQuerySupport.PERSISTED_QUERY_MARKER;

    private final static String APOLLO_AUTOMATIC_PERSISTED_QUERY_EXTENSION = "persistedQuery";


    @Internal
    private ExecutionInput(Builder builder) {
        this.query = assertQuery(builder);
        this.operationName = builder.operationName;
        this.context = builder.context;
        this.graphQLContext = assertNotNull(builder.graphQLContext);
        this.root = builder.root;
        this.rawVariables = builder.rawVariables;
        this.dataLoaderRegistry = builder.dataLoaderRegistry;
        this.executionId = builder.executionId;
        this.locale = builder.locale != null ? builder.locale : Locale.getDefault(); // always have a locale in place
        this.localContext = builder.localContext;
        this.extensions = builder.extensions;
        this.cancelled = builder.cancelled;
        this.profileExecution = builder.profileExecution;
    }

    private static String assertQuery(Builder builder) {
        if ((builder.query == null || builder.query.isEmpty()) && isPersistedQuery(builder)) {
            return PERSISTED_QUERY_MARKER;
        }

        return assertNotNull(builder.query, "query can't be null");
    }

    /**
     * This is used to determine if this execution input is a persisted query or not.
     *
     * @implNote The current implementation supports Apollo Persisted Queries (APQ) by checking for
     * the extensions property for the persisted query extension.
     * See <a href="https://www.apollographql.com/docs/apollo-server/performance/apq/">Apollo Persisted Queries</a> for more details.
     *
     * @param builder the builder to check
     *
     * @return true if this is a persisted query
     */
    private static boolean isPersistedQuery(Builder builder) {
        return builder.extensions != null &&
                builder.extensions.containsKey(APOLLO_AUTOMATIC_PERSISTED_QUERY_EXTENSION);
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
    @Nullable
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
    @Deprecated(since = "2021-07-05")
    @Nullable
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
    @Nullable
    public Object getLocalContext() {
        return localContext;
    }

    /**
     * @return the root object to start the query execution on
     */
    @Nullable
    public Object getRoot() {
        return root;
    }

    /**
     * @return a map of raw variables that can be referenced via $syntax in the query.
     */
    public Map<String, Object> getVariables() {
        return rawVariables.toMap();
    }

    /**
     * @return a map of raw variables that can be referenced via $syntax in the query.
     */
    public RawVariables getRawVariables() {
        return rawVariables;
    }

    /**
     * @return the data loader registry associated with this execution
     */
    public DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry;
    }


    /**
     * This value can be null before the execution starts, but once the execution starts, it will be set to a non-null value.
     * See #getExecutionIdNonNull() for a non-null version of this.
     *
     * @return Id that will be/was used to execute this operation.
     */
    @Nullable
    public ExecutionId getExecutionId() {
        return executionId;
    }


    /**
     * Once the execution starts, GraphQL Java will make sure that this execution id is non-null.
     * Therefore use this method if you are sue that the execution has started to get a guaranteed non-null execution id.
     *
     * @return the non null execution id of this operation.
     */
    public ExecutionId getExecutionIdNonNull() {
        return Assert.assertNotNull(this.executionId);
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
     * The graphql engine will check this frequently and if that is true, it will
     * throw a {@link graphql.execution.AbortExecutionException} to cancel the execution.
     * <p>
     * This is a cooperative cancellation.  Some asynchronous data fetching code may still continue to
     * run but there will be no more efforts run future field fetches say.
     *
     * @return true if the execution should be cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * This can be called to cancel the graphql execution.  Remember this is a cooperative cancellation
     * and the graphql engine needs to be running on a thread to allow is to respect this flag.
     */
    public void cancel() {
        cancelled.set(true);
    }


    public boolean isProfileExecution() {
        return profileExecution;
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
                .internalTransferContext(this.graphQLContext)
                .internalTransferCancelBoolean(this.cancelled)
                .localContext(this.localContext)
                .root(this.root)
                .dataLoaderRegistry(this.dataLoaderRegistry)
                .variables(this.rawVariables.toMap())
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
                ", rawVariables=" + rawVariables +
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

    @NullUnmarked
    public static class Builder {

        private String query;
        private String operationName;
        private GraphQLContext graphQLContext = GraphQLContext.newContext().build();
        private Object context = graphQLContext; // we make these the same object on purpose - legacy code will get the same object if this change nothing
        private Object localContext;
        private Object root;
        private RawVariables rawVariables = RawVariables.emptyVariables();
        private Map<String, Object> extensions = ImmutableKit.emptyMap();
        //
        // this is important - it allows code to later known if we never really set a dataloader and hence it can optimize
        // dataloader field tracking away.
        //
        private DataLoaderRegistry dataLoaderRegistry = EMPTY_DATALOADER_REGISTRY;
        private Locale locale = Locale.getDefault();
        private ExecutionId executionId;
        private AtomicBoolean cancelled = new AtomicBoolean(false);
        private boolean profileExecution;

        /**
         * Package level access to the graphql context
         *
         * @return shhh but it's the graphql context
         */
        GraphQLContext graphQLContext() {
            return graphQLContext;
        }

        public Builder query(String query) {
            this.query = query;
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
        @Deprecated(since = "2021-07-05")
        public Builder context(Object context) {
            this.context = context;
            return this;
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
        private Builder internalTransferContext(GraphQLContext graphQLContext) {
            this.graphQLContext = Assert.assertNotNull(graphQLContext);
            return this;
        }

        // hidden on purpose
        private Builder internalTransferCancelBoolean(AtomicBoolean cancelled) {
            this.cancelled = cancelled;
            return this;
        }


        public Builder root(Object root) {
            this.root = root;
            return this;
        }

        /**
         * Adds raw (not coerced) variables
         *
         * @param rawVariables the map of raw variables
         *
         * @return this builder
         */
        public Builder variables(Map<String, Object> rawVariables) {
            assertNotNull(rawVariables, "variables map can't be null");
            this.rawVariables = RawVariables.of(rawVariables);
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions = assertNotNull(extensions, "extensions map can't be null");
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

        public Builder profileExecution(boolean profileExecution) {
            this.profileExecution = profileExecution;
            return this;
        }

        public ExecutionInput build() {
            return new ExecutionInput(this);
        }
    }
}