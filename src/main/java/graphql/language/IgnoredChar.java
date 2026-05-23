package graphql.language;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Graphql syntax has a series of characters, such as spaces, new lines and commas that are not considered relevant
 * to the syntax.  However they can be captured and associated with the AST elements they belong to.
 *
 * This costs more memory but for certain use cases (like editors) this maybe be useful
 */
@PublicApi
@NullMarked
public class IgnoredChar implements Serializable {

    public enum IgnoredCharKind {
        SPACE, COMMA, TAB, CR, LF, OTHER
    }

    private final String value;
    private final IgnoredCharKind kind;
    private final SourceLocation sourceLocation;


    public IgnoredChar(String value, IgnoredCharKind kind, SourceLocation sourceLocation) {
        this.value = value;
        this.kind = kind;
        this.sourceLocation = sourceLocation;
    }

    public String getValue() {
        return value;
    }

    public IgnoredCharKind getKind() {
        return kind;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public String toString() {
        return "IgnoredChar{" +
                "value='" + value + '\'' +
                ", kind=" + kind +
                ", sourceLocation=" + sourceLocation +
                '}';
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IgnoredChar that = (IgnoredChar) o;
        return Objects.equals(value, that.value) &&
                kind == that.kind &&
                Objects.equals(sourceLocation, that.sourceLocation);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(value);
        result = 31 * result + Objects.hashCode(kind);
        result = 31 * result + Objects.hashCode(sourceLocation);
        return result;
    }
}
