package graphql;

import graphql.introspection.GoodFaithIntrospection;
import graphql.parser.ParserOptions;
import graphql.schema.PropertyDataFetcherHelper;

import static graphql.Assert.assertNotNull;

/**
 * This allows you to control specific aspects of the GraphQL system
 * including some JVM wide settings and some per execution settings
 * as well as experimental ones.
 */
public class GraphQLConfiguration {
    GraphQLConfiguration() {
    }

    /**
     * @return an element that allows you to control JVM wide parsing configuration
     */
    public ParserCfg parsing() {
        return new ParserCfg(this);
    }

    /**
     * @return an element that allows you to control JVM wide {@link graphql.schema.PropertyDataFetcher} configuration
     */
    public PropertyDataFetcherCfg propertyDataFetching() {
        return new PropertyDataFetcherCfg(this);
    }

    /**
     * @return an element that allows you to control JVM wide configuration
     * of {@link graphql.introspection.GoodFaithIntrospection}
     */
    public GoodFaithIntrospectionCfg goodFaithIntrospection() {
        return new GoodFaithIntrospectionCfg(this);
    }

    private static class BaseCfg {
        protected final GraphQLConfiguration configuration;

        private BaseCfg(GraphQLConfiguration configuration) {
            this.configuration = configuration;
        }

        /**
         * @return an element that allows you to chain multiple configuration elements
         */
        public GraphQLConfiguration then() {
            return configuration;
        }
    }

    public static class ParserCfg extends BaseCfg {

        private ParserCfg(GraphQLConfiguration configuration) {
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
        public ParserCfg setDefaultParserOptions(ParserOptions options) {
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
        public ParserCfg setDefaultOperationParserOptions(ParserOptions options) {
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
        public ParserCfg setDefaultSdlParserOptions(ParserOptions options) {
            ParserOptions.setDefaultSdlParserOptions(options);
            return this;
        }
    }

    public static class PropertyDataFetcherCfg extends BaseCfg {
        private PropertyDataFetcherCfg(GraphQLConfiguration configuration) {
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
        public PropertyDataFetcherCfg clearReflectionCache() {
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

    public static class GoodFaithIntrospectionCfg extends BaseCfg {
        private GoodFaithIntrospectionCfg(GraphQLConfiguration configuration) {
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
        public GoodFaithIntrospectionCfg enabledJvmWide(boolean enabled) {
            GoodFaithIntrospection.enabledJvmWide(enabled);
            return this;
        }
    }

    /*
     * ===============================================
     * Per GraphqlContext code down here
     * ===============================================
     */

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
        public IncrementalSupportCfg incrementalSupport() {
            return new IncrementalSupportCfg(this);
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
    }

    private static class BaseContextCfg {
        protected final GraphQLContextConfiguration contextConfig;

        private BaseContextCfg(GraphQLContextConfiguration contextConfig) {
            this.contextConfig = contextConfig;
        }

        /**
         * @return an element that allows you to chain multiple configuration elements
         */
        public GraphQLContextConfiguration then() {
            return contextConfig;
        }
    }

    public static class IncrementalSupportCfg extends BaseContextCfg {
        private IncrementalSupportCfg(GraphQLContextConfiguration contextConfig) {
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
        public IncrementalSupportCfg enableIncrementalSupport(boolean enable) {
            contextConfig.put(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, enable);
            return this;
        }
    }
}
