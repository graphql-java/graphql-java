package graphql.language;


/**
 * <p>SourceLocation class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class SourceLocation {

    private final int line;
    private final int column;

    /**
     * <p>Constructor for SourceLocation.</p>
     *
     * @param line a int.
     * @param column a int.
     */
    public SourceLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    /**
     * <p>Getter for the field <code>line</code>.</p>
     *
     * @return a int.
     */
    public int getLine() {
        return line;
    }

    /**
     * <p>Getter for the field <code>column</code>.</p>
     *
     * @return a int.
     */
    public int getColumn() {
        return column;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SourceLocation that = (SourceLocation) o;

        if (line != that.line) return false;
        return column == that.column;

    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = line;
        result = 31 * result + column;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "SourceLocation{" +
                "line=" + line +
                ", column=" + column +
                '}';
    }
}
