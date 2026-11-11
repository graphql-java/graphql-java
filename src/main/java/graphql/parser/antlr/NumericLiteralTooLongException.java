package graphql.parser.antlr;

import graphql.Internal;

@Internal
public class NumericLiteralTooLongException extends RuntimeException {

    private final int line;
    private final int column;
    private final int maxCharacters;

    public NumericLiteralTooLongException(int line, int column, int maxCharacters) {
        this.line = line;
        this.column = column;
        this.maxCharacters = maxCharacters;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getMaxCharacters() {
        return maxCharacters;
    }
}
