package graphql;

import graphql.parser.ParserOptions;

/**
 * This allows you to control specific aspects of the GraphQL system
 * including some JVM wide settings and some per execution settings
 * as well as experimental ones
 */
public class GraphQLConfiguration {
    static final GraphQLConfiguration INSTANCE = new GraphQLConfiguration();
    static final ParserCfg PARSER_CFG = new ParserCfg();
    static final IncrementalSupportCfg INCREMENTAL_SUPPORT_CFG = new IncrementalSupportCfg();

    private GraphQLConfiguration() {
    }

    public ParserCfg parser() {
        return PARSER_CFG;
    }

    @ExperimentalApi
    public IncrementalSupportCfg incrementalSupport() {
        return INCREMENTAL_SUPPORT_CFG;
    }

    public static class ParserCfg {

        private ParserCfg() {
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


    public static class IncrementalSupportCfg {
        private IncrementalSupportCfg() {
        }

        @ExperimentalApi
        public boolean isIncrementalSupportEnabled(GraphQLContext graphQLContext) {
            return graphQLContext.getBoolean(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT);
        }

        /**
         * This controls whether @defer and @stream behaviour is enabled for this execution.
         */
        @ExperimentalApi
        public IncrementalSupportCfg enableIncrementalSupport(GraphQLContext.Builder graphqlContext, boolean enable) {
            graphqlContext.put(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, enable);
            return this;
        }
    }
}
