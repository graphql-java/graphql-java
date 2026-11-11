package graphql.parser.antlr;

import graphql.Internal;

@Internal
public class NumericLiteralTooLongException extends RuntimeException {

    private final int line;
    private final int column;
    private final int startIndex;
    private final int stopIndex;
    private final int maxCharacters;

    public NumericLiteralTooLongException(int line, int column, int startIndex, int stopIndex, int maxCharacters) {
        this.line = line;
        this.column = column;
        this.startIndex = startIndex;
        this.stopIndex = stopIndex;
        this.maxCharacters = maxCharacters;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getStopIndex() {
        return stopIndex;
    }

    public int getMaxCharacters() {
        return maxCharacters;
    }
}
