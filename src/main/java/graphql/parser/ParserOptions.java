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
     * An graphql hacking vector is to send nonsensical queries that burn lots of parsing CPU time and burn
     * memory representing a document that wont ever execute.  To prevent this for most users, graphql-java
     * set this value to 15000.  ANTLR parsing time is linear to the number of tokens presented.  The more you
     * allow the longer it takes.
     *
     * If you want to allow more, then {@link #setDefaultParserOptions(ParserOptions)} allows you to change this
     * JVM wide.
     */
    public static int MAX_QUERY_TOKENS = 15000;

    private static ParserOptions defaultJvmParserOptions = newParserOptions()
            .captureIgnoredChars(false)
            .captureSourceLocation(true)
            .maxTokens(MAX_QUERY_TOKENS) // to prevent a billion laughs style attacks, we set a default for graphql-java

            .build();

    /**
     * By default the Parser will not capture ignored characters.  A static holds this default
     * value in a JVM wide basis options object.
     *
     * Significant memory savings can be made if we do NOT capture ignored characters,
     * especially in SDL parsing.
     *
     * @return the static default value on whether to capture ignored chars
     *
     * @see graphql.language.IgnoredChar
     * @see graphql.language.SourceLocation
     */
    public static ParserOptions getDefaultParserOptions() {
        return defaultJvmParserOptions;
    }

    /**
     * By default the Parser will not capture ignored characters.  A static holds this default
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

    private final boolean captureIgnoredChars;
    private final boolean captureSourceLocation;
    private final int maxTokens;
    private final ParsingListener parsingListener;

    private ParserOptions(Builder builder) {
        this.captureIgnoredChars = builder.captureIgnoredChars;
        this.captureSourceLocation = builder.captureSourceLocation;
        this.maxTokens = builder.maxTokens;
        this.parsingListener = builder.parsingListener;
    }

    /**
     * Significant memory savings can be made if we do NOT capture ignored characters,
     * especially in SDL parsing.  So we have set this to false by default.
     *
     * @return true if ignored chars are captured in AST nodes
     */
    public boolean isCaptureIgnoredChars() {
        return captureIgnoredChars;
    }


    /**
     * Memory savings can be made if we do NOT set {@link graphql.language.SourceLocation}s
     * on AST nodes,  especially in SDL parsing.
     *
     * @return true if {@link graphql.language.SourceLocation}s are captured in AST nodes
     *
     * @see graphql.language.SourceLocation
     */
    public boolean isCaptureSourceLocation() {
        return captureSourceLocation;
    }

    /**
     * A graphql hacking vector is to send nonsensical queries that burn lots of parsing CPU time and burn
     * memory representing a document that won't ever execute.  To prevent this you can set a maximum number of parse
     * tokens that will be accepted before an exception is thrown and the parsing is stopped.
     *
     * @return the maximum number of raw tokens the parser will accept, after which an exception will be thrown.
     */
    public int getMaxTokens() {
        return maxTokens;
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
        private int maxTokens = MAX_QUERY_TOKENS;
        private ParsingListener parsingListener = ParsingListener.NOOP;

        Builder() {
        }

        Builder(ParserOptions parserOptions) {
            this.captureIgnoredChars = parserOptions.captureIgnoredChars;
            this.captureSourceLocation = parserOptions.captureSourceLocation;
            this.maxTokens = parserOptions.maxTokens;
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

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
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
