package graphql.parser;

import graphql.Assert;
import graphql.PublicApi;

/**
 * Options that control how the {@link Parser} behaves.
 */
@PublicApi
public class ParserOptions {

    private static ParserOptions defaultJvmParserOptions = newParserOptions().captureIgnoredChars(false).build();

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
     */
    public static void setDefaultParserOptions(ParserOptions options) {
        defaultJvmParserOptions = Assert.assertNotNull(options);
    }

    private final boolean captureIgnoredChars;

    private ParserOptions(Builder builder) {
        this.captureIgnoredChars = builder.captureIgnoredChars;
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

    public static Builder newParserOptions() {
        return new Builder();
    }

    public static class Builder {

        private boolean captureIgnoredChars = false;

        public Builder captureIgnoredChars(boolean captureIgnoredChars) {
            this.captureIgnoredChars = captureIgnoredChars;
            return this;
        }

        public ParserOptions build() {
            return new ParserOptions(this);
        }

    }

}
