package graphql.language;


public class SourceLocation {

    private final int line;
    private final int positionInLine;

    public SourceLocation(int line, int positionInLine) {
        this.line = line;
        this.positionInLine = positionInLine;
    }

    public int getLine() {
        return line;
    }

    public int getPositionInLine() {
        return positionInLine;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SourceLocation that = (SourceLocation) o;

        if (line != that.line) return false;
        return positionInLine == that.positionInLine;

    }

    @Override
    public int hashCode() {
        int result = line;
        result = 31 * result + positionInLine;
        return result;
    }

    @Override
    public String toString() {
        return "SourceLocation{" +
                "line=" + line +
                ", positionInLine=" + positionInLine +
                '}';
    }
}
