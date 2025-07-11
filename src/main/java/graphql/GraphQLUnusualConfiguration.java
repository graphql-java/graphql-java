package graphql;

import graphql.execution.ResponseMapFactory;
import graphql.execution.instrumentation.dataloader.DelayedDataLoaderDispatcherExecutorFactory;
import graphql.introspection.GoodFaithIntrospection;
import graphql.parser.ParserOptions;
import graphql.schema.PropertyDataFetcherHelper;

import java.time.Duration;

import static graphql.Assert.assertNotNull;
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS;
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY;
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING;

/**
 * This allows you to control "unusual" aspects of the GraphQL system
 * including some JVM wide settings and some per execution settings
 * as well as experimental ones.
 * <p>
 * This is named unusual because in general we don't expect you to
 * have to make ths configuration by default, but you can opt into certain features
 * or disable them if you want to.
 */
public class GraphQLUnusualConfiguration {
    GraphQLUnusualConfiguration() {
    }

    /**
     * @return an element that allows you to control JVM wide parsing configuration
     */
    public ParserConfig parsing() {
        return new ParserConfig(this);
    }

    /**
     * @return an element that allows you to control JVM wide {@link graphql.schema.PropertyDataFetcher} configuration
     */
    public PropertyDataFetcherConfig propertyDataFetching() {
        return new PropertyDataFetcherConfig(this);
    }

    /**
     * @return an element that allows you to control JVM wide configuration
     * of {@link graphql.introspection.GoodFaithIntrospection}
     */
    public GoodFaithIntrospectionConfig goodFaithIntrospection() {
        return new GoodFaithIntrospectionConfig(this);
    }

    private static class BaseConfig {
        protected final GraphQLUnusualConfiguration configuration;

        private BaseConfig(GraphQLUnusualConfiguration configuration) {
            this.configuration = configuration;
        }

        /**
         * @return an element that allows you to chain multiple configuration elements
         */
        public GraphQLUnusualConfiguration then() {
            return configuration;
        }
    }

    public static class ParserConfig extends BaseConfig {

        private ParserConfig(GraphQLUnusualConfiguration configuration) {
            super(configuration);
        }

        /**
         * By default, the Parser will not capture ignored characters.  A static holds this default
         * value in a JVM wide basis options object.
         * <p>
         * Significant memory savings can be made if we do NOT capture ignored characters,
         * especially in SDL parsing.
         *
         * @return the static default JVM value
         *
         * @see graphql.language.IgnoredChar
         * @see graphql.language.SourceLocation
         */
        public ParserOptions getDefaultParserOptions() {
            return ParserOptions.getDefaultParserOptions();
        }

        /**
         * By default, the Parser will not capture ignored characters.  A static holds this default
         * value in a JVM wide basis options object.
         * <p>
         * Significant memory savings can be made if we do NOT capture ignored characters,
         * especially in SDL parsing.  So we have set this to false by default.
         * <p>
         * This static can be set to true to allow the behavior of version 16.x or before.
         *
         * @param options - the new default JVM parser options
         *
         * @see graphql.language.IgnoredChar
         * @see graphql.language.SourceLocation
         */
        public ParserConfig setDefaultParserOptions(ParserOptions options) {
            ParserOptions.setDefaultParserOptions(options);
            return this;
        }


        /**
         * By default, for operation parsing, the Parser will not capture ignored characters, and it will not capture line comments into AST
         * elements .  A static holds this default value for operation parsing in a JVM wide basis options object.
         *
         * @return the static default JVM value for operation parsing
         *
         * @see graphql.language.IgnoredChar
         * @see graphql.language.SourceLocation
         */
        public ParserOptions getDefaultOperationParserOptions() {
            return ParserOptions.getDefaultOperationParserOptions();
        }

        /**
         * By default, the Parser will not capture ignored characters or line comments.  A static holds this default
         * value in a JVM wide basis options object for operation parsing.
         * <p>
         * This static can be set to true to allow the behavior of version 16.x or before.
         *
         * @param options - the new default JVM parser options for operation parsing
         *
         * @see graphql.language.IgnoredChar
         * @see graphql.language.SourceLocation
         */
        public ParserConfig setDefaultOperationParserOptions(ParserOptions options) {
            ParserOptions.setDefaultOperationParserOptions(options);
            return this;
        }

        /**
         * By default, for SDL parsing, the Parser will not capture ignored characters, but it will capture line comments into AST
         * elements.  The SDL default options allow unlimited tokens and whitespace, since a DOS attack vector is
         * not commonly available via schema SDL parsing.
         * <p>
         * A static holds this default value for SDL parsing in a JVM wide basis options object.
         *
         * @return the static default JVM value for SDL parsing
         *
         * @see graphql.language.IgnoredChar
         * @see graphql.language.SourceLocation
         * @see graphql.schema.idl.SchemaParser
         */
        public ParserOptions getDefaultSdlParserOptions() {
            return ParserOptions.getDefaultSdlParserOptions();
        }

        /**
         * By default, for SDL parsing, the Parser will not capture ignored characters, but it will capture line comments into AST
         * elements .  A static holds this default value for operation parsing in a JVM wide basis options object.
         * <p>
         * This static can be set to true to allow the behavior of version 16.x or before.
         *
         * @param options - the new default JVM parser options for SDL parsing
         *
         * @see graphql.language.IgnoredChar
         * @see graphql.language.SourceLocation
         */
        public ParserConfig setDefaultSdlParserOptions(ParserOptions options) {
            ParserOptions.setDefaultSdlParserOptions(options);
            return this;
        }
    }

    public static class PropertyDataFetcherConfig extends BaseConfig {
        private PropertyDataFetcherConfig(GraphQLUnusualConfiguration configuration) {
            super(configuration);
        }

        /**
         * PropertyDataFetcher caches the methods and fields that map from a class to a property for runtime performance reasons
         * as well as negative misses.
         * <p>
         * However during development you might be using an assistance tool like JRebel to allow you to tweak your code base and this
         * caching may interfere with this.  So you can call this method to clear the cache.  A JRebel plugin could
         * be developed to do just that.
         */
        @SuppressWarnings("unused")
        public PropertyDataFetcherConfig clearReflectionCache() {
            PropertyDataFetcherHelper.clearReflectionCache();
            return this;
        }

        /**
         * This can be used to control whether PropertyDataFetcher will use {@link java.lang.reflect.Method#setAccessible(boolean)} to gain access to property
         * values.  By default, it PropertyDataFetcher WILL use setAccessible.
         *
         * @param flag whether to use setAccessible
         *
         * @return the previous value of the flag
         */
        public boolean setUseSetAccessible(boolean flag) {
            return PropertyDataFetcherHelper.setUseSetAccessible(flag);
        }

        /**
         * This can be used to control whether PropertyDataFetcher will cache negative lookups for a property for performance reasons.  By default it PropertyDataFetcher WILL cache misses.
         *
         * @param flag whether to cache misses
         *
         * @return the previous value of the flag
         */
        public boolean setUseNegativeCache(boolean flag) {
            return PropertyDataFetcherHelper.setUseNegativeCache(flag);
        }
    }

    public static class GoodFaithIntrospectionConfig extends BaseConfig {
        private GoodFaithIntrospectionConfig(GraphQLUnusualConfiguration configuration) {
            super(configuration);
        }

        /**
         * @return true if good faith introspection is enabled
         */
        public boolean isEnabledJvmWide() {
            return GoodFaithIntrospection.isEnabledJvmWide();
        }

        /**
         * This allows you to disable good faith introspection, which is on by default.
         *
         * @param enabled the desired state
         *
         * @return the previous state
         */
        public GoodFaithIntrospectionConfig enabledJvmWide(boolean enabled) {
            GoodFaithIntrospection.enabledJvmWide(enabled);
            return this;
        }
    }

    /*
     * ===============================================
     * Per GraphqlContext code down here
     * ===============================================
     */

    @SuppressWarnings("DataFlowIssue")
    public static class GraphQLContextConfiguration {
        // it will be one or the other types of GraphQLContext
        private final GraphQLContext graphQLContext;
        private final GraphQLContext.Builder graphQLContextBuilder;

        GraphQLContextConfiguration(GraphQLContext graphQLContext) {
            this.graphQLContext = graphQLContext;
            this.graphQLContextBuilder = null;
        }

        GraphQLContextConfiguration(GraphQLContext.Builder graphQLContextBuilder) {
            this.graphQLContextBuilder = graphQLContextBuilder;
            this.graphQLContext = null;
        }

        /**
         * @return an element that allows you to control incremental support, that is @defer configuration
         */
        public IncrementalSupportConfig incrementalSupport() {
            return new IncrementalSupportConfig(this);
        }

        /**
         * @return an element that allows you to control normalized document behavior
         */
        public NormalizedDocumentSupportConfig normalizedDocumentSupport() {
            return new NormalizedDocumentSupportConfig(this);
        }

        /**
         * @return an element that allows you to precisely control {@link org.dataloader.DataLoader} behavior
         * in graphql-java.
         */
        public DataloaderConfig dataloaderConfig() {
            return new DataloaderConfig(this);
        }

        /**
         * @return an element that allows you to control the {@link ResponseMapFactory} used
         */
        public ResponseMapFactoryConfig responseMapFactory() {
            return new ResponseMapFactoryConfig(this);
        }

        private void put(String named, Object value) {
            if (graphQLContext != null) {
                graphQLContext.put(named, value);
            } else {
                assertNotNull(graphQLContextBuilder).put(named, value);
            }
        }

        private boolean getBoolean(String named) {
            if (graphQLContext != null) {
                return graphQLContext.getBoolean(named);
            } else {
                return assertNotNull(graphQLContextBuilder).getBoolean(named);
            }
        }

        private <T> T get(String named) {
            if (graphQLContext != null) {
                return graphQLContext.get(named);
            } else {
                //noinspection unchecked
                return (T) assertNotNull(graphQLContextBuilder).get(named);
            }
        }
    }

    private static class BaseContextConfig {
        protected final GraphQLContextConfiguration contextConfig;

        private BaseContextConfig(GraphQLContextConfiguration contextConfig) {
            this.contextConfig = contextConfig;
        }

        /**
         * @return an element that allows you to chain multiple configuration elements
         */
        public GraphQLContextConfiguration then() {
            return contextConfig;
        }
    }

    public static class NormalizedDocumentSupportConfig extends BaseContextConfig {
        private NormalizedDocumentSupportConfig(GraphQLContextConfiguration contextConfig) {
            super(contextConfig);
        }

        /**
         * @return true if normalized document behaviour is enabled for this execution.
         */
        public boolean isNormalizedDocumentSupportEnabled() {
            return contextConfig.getBoolean(ExperimentalApi.ENABLE_NORMALIZED_DOCUMENT_SUPPORT);
        }

        /**
         * This controls whether normalized document behaviour is enabled for this execution.
         */
        @ExperimentalApi
        public NormalizedDocumentSupportConfig enableNormalizedDocumentSupport(boolean enable) {
            contextConfig.put(ExperimentalApi.ENABLE_NORMALIZED_DOCUMENT_SUPPORT, enable);
            return this;
        }
    }

    public static class IncrementalSupportConfig extends BaseContextConfig {
        private IncrementalSupportConfig(GraphQLContextConfiguration contextConfig) {
            super(contextConfig);
        }

        /**
         * @return true if @defer and @stream behaviour is enabled for this execution.
         */
        public boolean isIncrementalSupportEnabled() {
            return contextConfig.getBoolean(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT);
        }

        /**
         * This controls whether @defer and @stream behaviour is enabled for this execution.
         */
        @ExperimentalApi
        public IncrementalSupportConfig enableIncrementalSupport(boolean enable) {
            contextConfig.put(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, enable);
            return this;
        }
    }

    public static class DataloaderConfig extends BaseContextConfig {
        private DataloaderConfig(GraphQLContextConfiguration contextConfig) {
            super(contextConfig);
        }

        /**
         * @return true if @defer and @stream behaviour is enabled for this execution.
         */
        public boolean isDataLoaderChainingEnabled() {
            return contextConfig.getBoolean(ENABLE_DATA_LOADER_CHAINING);
        }

        /**
         * Enables the ability that chained DataLoaders are dispatched automatically.
         */
        @ExperimentalApi
        public DataloaderConfig enableDataLoaderChaining(boolean enable) {
            contextConfig.put(ENABLE_DATA_LOADER_CHAINING, enable);
            return this;
        }

        /**
         * @return the batch window duration size for delayed DataLoaders.
         */
        public Duration delayedDataLoaderBatchWindowSize() {
            Long d = contextConfig.get(DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS);
            return d != null ? Duration.ofNanos(d) : null;
        }

        /**
         * Sets the batch window duration size for delayed DataLoaders.
         * That is for DataLoaders, that are not batched as part of the normal per level
         * dispatching, because they were created after the level was already dispatched.
         */
        @ExperimentalApi
        public DataloaderConfig delayedDataLoaderBatchWindowSize(Duration batchWindowSize) {
            contextConfig.put(DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS, batchWindowSize.toNanos());
            return this;
        }

        /**
         * @return the instance of {@link DelayedDataLoaderDispatcherExecutorFactory} that is used to create the
         * {@link java.util.concurrent.ScheduledExecutorService} for the delayed DataLoader dispatching.
         */
        public DelayedDataLoaderDispatcherExecutorFactory delayedDataLoaderExecutorFactory() {
            return contextConfig.get(DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY);
        }

        /**
         * Sets the instance of {@link DelayedDataLoaderDispatcherExecutorFactory} that is used to create the
         * {@link java.util.concurrent.ScheduledExecutorService} for the delayed DataLoader dispatching.
         */
        @ExperimentalApi
        public DataloaderConfig delayedDataLoaderExecutorFactory(DelayedDataLoaderDispatcherExecutorFactory delayedDataLoaderDispatcherExecutorFactory) {
            contextConfig.put(DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY, delayedDataLoaderDispatcherExecutorFactory);
            return this;
        }
    }

    public static class ResponseMapFactoryConfig extends BaseContextConfig {
        private ResponseMapFactoryConfig(GraphQLContextConfiguration contextConfig) {
            super(contextConfig);
        }

        /**
         * @return the {@link ResponseMapFactory} in play - this can be null
         */
        @ExperimentalApi
        public ResponseMapFactory getOr(ResponseMapFactory defaultFactory) {
            ResponseMapFactory responseMapFactory = contextConfig.get(ResponseMapFactory.class.getCanonicalName());
            return responseMapFactory != null ? responseMapFactory : defaultFactory;
        }

        /**
         * This controls the {@link ResponseMapFactory} to use for this request
         */
        @ExperimentalApi
        public ResponseMapFactoryConfig setFactory(ResponseMapFactory factory) {
            contextConfig.put(ResponseMapFactory.class.getCanonicalName(), factory);
            return this;
        }
    }
}
