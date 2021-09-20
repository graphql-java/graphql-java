package graphql.parser;

import graphql.PublicSpi;

/**
 * This listener interface is invoked for each token parsed by the graphql parser code.
 */
@PublicSpi
public interface ParsingListener {

    /**
     * A NoOp implementation of {@link ParsingListener}
     */
    ParsingListener NOOP = t -> {
    };


    /**
     * This represents a token symbol that has been parsed
     */
    interface Symbol {
        String getText();

        int getLine();

        int getCharPositionInLine();
    }

    /**
     * This is called for each token found during parsing
     *
     * @param symbol the symbol found
     */
    void onToken(Symbol symbol);
}
