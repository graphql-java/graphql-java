package graphql.language;


public class SourceLocation {

    private final int line;
    private final int positionInLine;

    public SourceLocation(int line, int positionInLine) {
        this.line = line;
        this.positionInLine = positionInLine;
    }
}
