package graphql.parser;

import graphql.PublicApi;

import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * Options that control how the {@link Parser} behaves.
 */
@PublicApi
public class ParserOptions {
    /**
     * A graphql hacking vector is to send nonsensical queries with large tokens that contain a repeated characters
     * that burn lots of parsing CPU time and burn memory representing a document that won't ever execute.
     * To prevent this for most users, graphql-java sets this value to 1MB.
     * ANTLR parsing time is linear to the number of characters presented.  The more you
     * allow the longer it takes.
     * <p>
     * If you want to allow more, then {@link #setDefaultParserOptions(ParserOptions)} allows you to change this
     * JVM wide.
     */
    public static final int MAX_QUERY_CHARACTERS = 1024 * 1024; // 1 MB

    /**
     * A graphql hacking vector is to send nonsensical queries with lots of tokens that burn lots of parsing CPU time and burn
     * memory representing a document that won't ever execute.  To prevent this for most users, graphql-java
     * sets this value to 15000.  ANTLR parsing time is linear to the number of tokens presented.  The more you
     * allow the longer it takes.
     * <p>
     * If you want to allow more, then {@link #setDefaultParserOptions(ParserOptions)} allows you to change this
     * JVM wide.
     */
    public static final int MAX_QUERY_TOKENS = 15_000;
    /**
     * Another graphql hacking vector is to send large amounts of whitespace in operations that burn lots of parsing CPU time and burn
     * memory representing a document.  Whitespace token processing in ANTLR is 2 orders of magnitude faster than grammar token processing
     * however it still takes some time to happen.
     * <p>
     * If you want to allow more, then {@link #setDefaultParserOptions(ParserOptions)} allows you to change this
     * JVM wide.
     */
    public static final int MAX_WHITESPACE_TOKENS = 200_000;

    /**
     * A graphql hacking vector is to send nonsensical queries that have lots of grammar rule depth to them which
     * can cause stack overflow exceptions during the query parsing.  To prevent this for most users, graphql-java
     * sets this value to 500 grammar rules deep.
     * <p>
     * If you want to allow more, then {@link #setDefaultParserOptions(ParserOptions)} allows you to change this
     * JVM wide.
     */
    public static final int MAX_RULE_DEPTH = 500;

    private static ParserOptions defaultJvmParserOptions = newParserOptions()
            .captureIgnoredChars(false)
            .captureSourceLocation(true)
            .captureLineComments(true)
            .readerTrackData(true)
            .maxCharacters(MAX_QUERY_CHARACTERS)
            .maxTokens(MAX_QUERY_TOKENS) // to prevent a billion laughs style attacks, we set a default for graphql-java
            .maxWhitespaceTokens(MAX_WHITESPACE_TOKENS)
            .maxRuleDepth(MAX_RULE_DEPTH)
            .build();

    private static ParserOptions defaultJvmOperationParserOptions = newParserOptions()
            .captureIgnoredChars(false)
            .captureSourceLocation(true)
            .captureLineComments(false) // #comments are not useful in query parsing
            .readerTrackData(true)
            .maxCharacters(MAX_QUERY_CHARACTERS)
            .maxTokens(MAX_QUERY_TOKENS) // to prevent a billion laughs style attacks, we set a default for graphql-java
            .maxWhitespaceTokens(MAX_WHITESPACE_TOKENS)
            .maxRuleDepth(MAX_RULE_DEPTH)
            .build();

    private static ParserOptions defaultJvmSdlParserOptions = newParserOptions()
            .captureIgnoredChars(false)
            .captureSourceLocation(true)
            .captureLineComments(true) // #comments are useful in SDL parsing
            .readerTrackData(true)
            .maxCharacters(Integer.MAX_VALUE)
            .maxTokens(Integer.MAX_VALUE) // we are less worried about a billion laughs with SDL parsing since the call path is not facing attackers
            .maxWhitespaceTokens(Integer.MAX_VALUE)
            .maxRuleDepth(Integer.MAX_VALUE)
            .build();

    /**
     * By default, the Parser will not capture ignored characters.  A static holds this default
     * value in a JVM wide basis options object.
     *
     * Significant memory savings can be made if we do NOT capture ignored characters,
     * especially in SDL parsing.
     *
     * @return the static default JVM value
     *
     * @see graphql.language.IgnoredChar
     * @see graphql.language.SourceLocation
     */
    public static ParserOptions getDefaultParserOptions() {
        return defaultJvmParserOptions;
    }

    /**
     * By default, the Parser will not capture ignored characters.  A static holds this default
     * value in a JVM wide basis options object.
     *
     * Significant memory savings can be made if we do NOT capture ignored characters,
     * especially in SDL parsing.  So we have set this to false by default.
     *
     * This static can be set to true to allow the behavior of version 16.x or before.
     *
     * @param options - the new default JVM parser options
     *
     * @see graphql.language.IgnoredChar
     * @see graphql.language.SourceLocation
     */
    public static void setDefaultParserOptions(ParserOptions options) {
        defaultJvmParserOptions = assertNotNull(options);
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
    public static ParserOptions getDefaultOperationParserOptions() {
        return defaultJvmOperationParserOptions;
    }

    /**
     * By default, the Parser will not capture ignored characters or line comments.  A static holds this default
     * value in a JVM wide basis options object for operation parsing.
     *
     * This static can be set to true to allow the behavior of version 16.x or before.
     *
     * @param options - the new default JVM parser options for operation parsing
     *
     * @see graphql.language.IgnoredChar
     * @see graphql.language.SourceLocation
     */
    public static void setDefaultOperationParserOptions(ParserOptions options) {
        defaultJvmOperationParserOptions = assertNotNull(options);
    }

    /**
     * By default, for SDL parsing, the Parser will not capture ignored characters, but it will capture line comments into AST
     * elements.  The SDL default options allow unlimited tokens and whitespace, since a DOS attack vector is
     * not commonly available via schema SDL parsing.
     *
     * A static holds this default value for SDL parsing in a JVM wide basis options object.
     *
     * @return the static default JVM value for SDL parsing
     *
     * @see graphql.language.IgnoredChar
     * @see graphql.language.SourceLocation
     * @see graphql.schema.idl.SchemaParser
     */
    public static ParserOptions getDefaultSdlParserOptions() {
        return defaultJvmSdlParserOptions;
    }

    /**
     * By default, for SDL parsing, the Parser will not capture ignored characters, but it will capture line comments into AST
     * elements .  A static holds this default value for operation parsing in a JVM wide basis options object.
     *
     * This static can be set to true to allow the behavior of version 16.x or before.
     *
     * @param options - the new default JVM parser options for operation parsing
     *
     * @see graphql.language.IgnoredChar
     * @see graphql.language.SourceLocation
     */
    public static void setDefaultSdlParserOptions(ParserOptions options) {
        defaultJvmSdlParserOptions = assertNotNull(options);
    }

    private final boolean captureIgnoredChars;
    private final boolean captureSourceLocation;
    private final boolean captureLineComments;
    private final boolean readerTrackData;
    private final int maxCharacters;
    private final int maxTokens;
    private final int maxWhitespaceTokens;
    private final int maxRuleDepth;
    private final ParsingListener parsingListener;

    private ParserOptions(Builder builder) {
        this.captureIgnoredChars = builder.captureIgnoredChars;
        this.captureSourceLocation = builder.captureSourceLocation;
        this.captureLineComments = builder.captureLineComments;
        this.readerTrackData = builder.readerTrackData;
        this.maxCharacters = builder.maxCharacters;
        this.maxTokens = builder.maxTokens;
        this.maxWhitespaceTokens = builder.maxWhitespaceTokens;
        this.maxRuleDepth = builder.maxRuleDepth;
        this.parsingListener = builder.parsingListener;
    }

    /**
     * Significant memory savings can be made if we do NOT capture ignored characters,
     * especially in SDL parsing.  So we have set this to false by default.
     *
     * @return true if ignored chars should be captured as AST nodes
     */
    public boolean isCaptureIgnoredChars() {
        return captureIgnoredChars;
    }


    /**
     * Memory savings can be made if we do NOT set {@link graphql.language.SourceLocation}s
     * on AST nodes,  especially in SDL parsing.
     *
     * @return true if {@link graphql.language.SourceLocation}s should be captured as AST nodes
     *
     * @see graphql.language.SourceLocation
     */
    public boolean isCaptureSourceLocation() {
        return captureSourceLocation;
    }

    /**
     * Single-line {@link graphql.language.Comment}s do not have any semantic meaning in
     * GraphQL source documents, as such you may wish to ignore them.
     * <p>
     * This option does not ignore documentation {@link graphql.language.Description}s.
     *
     * @return true if {@link graphql.language.Comment}s should be captured as AST nodes
     *
     * @see graphql.language.SourceLocation
     */
    public boolean isCaptureLineComments() {
        return captureLineComments;
    }

    /**
     * Controls whether the underlying {@link MultiSourceReader} should track previously read data or not.
     *
     * @return true if {@link MultiSourceReader} should track data in memory.
     */
    public boolean isReaderTrackData() {
        return readerTrackData;
    }

    /**
     * A graphql hacking vector is to send nonsensical queries that contain a repeated characters that burn lots of parsing CPU time and burn
     * memory representing a document that won't ever execute.  To prevent this for most users, graphql-java
     * sets this value to 1MB.
     *
     * @return the maximum number of characters the parser will accept, after which an exception will be thrown.
     */
    public int getMaxCharacters() {
        return maxCharacters;
    }


    /**
     * A graphql hacking vector is to send nonsensical queries that burn lots of parsing CPU time and burns
     * memory representing a document that won't ever execute.  To prevent this you can set a maximum number of parse
     * tokens that will be accepted before an exception is thrown and the parsing is stopped.
     *
     * @return the maximum number of raw tokens the parser will accept, after which an exception will be thrown.
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * A graphql hacking vector is to send larges amounts of whitespace that burn lots of parsing CPU time and burn
     * memory representing a document.  To prevent this you can set a maximum number of whitespace parse
     * tokens that will be accepted before an exception is thrown and the parsing is stopped.
     *
     * @return the maximum number of raw whitespace tokens the parser will accept, after which an exception will be thrown.
     */
    public int getMaxWhitespaceTokens() {
        return maxWhitespaceTokens;
    }

    /**
     * A graphql hacking vector is to send nonsensical queries that have lots of rule depth to them which
     * can cause stack overflow exceptions during the query parsing.  To prevent this you can set a value
     * that is the maximum depth allowed before an exception is thrown and the parsing is stopped.
     *
     * @return the maximum token depth the parser will accept, after which an exception will be thrown.
     */
    public int getMaxRuleDepth() {
        return maxRuleDepth;
    }

    public ParsingListener getParsingListener() {
        return parsingListener;
    }

    public ParserOptions transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newParserOptions() {
        return new Builder();
    }

    public static class Builder {

        private boolean captureIgnoredChars = false;
        private boolean captureSourceLocation = true;
        private boolean captureLineComments = true;
        private boolean readerTrackData = true;
        private ParsingListener parsingListener = ParsingListener.NOOP;
        private int maxCharacters = MAX_QUERY_CHARACTERS;
        private int maxTokens = MAX_QUERY_TOKENS;
        private int maxWhitespaceTokens = MAX_WHITESPACE_TOKENS;
        private int maxRuleDepth = MAX_RULE_DEPTH;

        Builder() {
        }

        Builder(ParserOptions parserOptions) {
            this.captureIgnoredChars = parserOptions.captureIgnoredChars;
            this.captureSourceLocation = parserOptions.captureSourceLocation;
            this.captureLineComments = parserOptions.captureLineComments;
            this.maxCharacters = parserOptions.maxCharacters;
            this.maxTokens = parserOptions.maxTokens;
            this.maxWhitespaceTokens = parserOptions.maxWhitespaceTokens;
            this.maxRuleDepth = parserOptions.maxRuleDepth;
            this.parsingListener = parserOptions.parsingListener;
        }

        public Builder captureIgnoredChars(boolean captureIgnoredChars) {
            this.captureIgnoredChars = captureIgnoredChars;
            return this;
        }

        public Builder captureSourceLocation(boolean captureSourceLocation) {
            this.captureSourceLocation = captureSourceLocation;
            return this;
        }

        public Builder captureLineComments(boolean captureLineComments) {
            this.captureLineComments = captureLineComments;
            return this;
        }

        public Builder readerTrackData(boolean readerTrackData) {
            this.readerTrackData = readerTrackData;
            return this;
        }

        public Builder maxCharacters(int maxCharacters) {
            this.maxCharacters = maxCharacters;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder maxWhitespaceTokens(int maxWhitespaceTokens) {
            this.maxWhitespaceTokens = maxWhitespaceTokens;
            return this;
        }

        public Builder maxRuleDepth(int maxRuleDepth) {
            this.maxRuleDepth = maxRuleDepth;
            return this;
        }

        public Builder parsingListener(ParsingListener parsingListener) {
            this.parsingListener = assertNotNull(parsingListener);
            return this;
        }

        public ParserOptions build() {
            return new ParserOptions(this);
        }

    }

}
