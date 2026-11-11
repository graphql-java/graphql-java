package graphql.parser.antlr;

import graphql.Internal;

@Internal
public class NumericLiteralTooLongException extends RuntimeException {

    private final int line;
    private final int column;
    private final int startIndex;
    private final int stopIndex;
    private final int maxDigits;

    public NumericLiteralTooLongException(int line, int column, int startIndex, int stopIndex, int maxDigits) {
        this.line = line;
        this.column = column;
        this.startIndex = startIndex;
        this.stopIndex = stopIndex;
        this.maxDigits = maxDigits;
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

    public int getMaxDigits() {
        return maxDigits;
    }
}
