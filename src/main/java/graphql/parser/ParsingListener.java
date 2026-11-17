package graphql.parser;

import graphql.PublicSpi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * This listener interface is invoked for each token parsed by the graphql parser code.
 */
@NullMarked
@PublicSpi
public interface ParsingListener {

    /**
     * A NoOp implementation of {@link ParsingListener}
     */
    ParsingListener NOOP = t -> {
    };


    /**
     * This represents a token that has been parsed
     */
    interface Token {
        /**
         * @return the text of the parsed token
         */
        @Nullable String getText();

        /**
         * @return the line the token occurred on
         */
        int getLine();

        /**
         * @return the position within the line the token occurred on
         */
        int getCharPositionInLine();
    }

    /**
     * This is called for each token found during parsing
     *
     * @param token the token found
     */
    void onToken(Token token);
}
