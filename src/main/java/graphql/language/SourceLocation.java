package graphql.language;


import graphql.PublicApi;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.idl.SchemaGenerator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

@PublicApi
@NullMarked
public class SourceLocation implements Serializable {

    public static final SourceLocation EMPTY = new SourceLocation(-1, -1);

    private final int line;
    private final int column;
    private final @Nullable String sourceName;

    public SourceLocation(int line, int column) {
        this(line, column, null);
    }

    public SourceLocation(int line, int column, @Nullable String sourceName) {
        this.line = line;
        this.column = column;
        this.sourceName = sourceName;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public @Nullable String getSourceName() {
        return sourceName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SourceLocation that = (SourceLocation) o;

        if (line != that.line) {
            return false;
        }
        if (column != that.column) {
            return false;
        }
        return Objects.equals(sourceName, that.sourceName);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Integer.hashCode(line);
        result = 31 * result + Integer.hashCode(column);
        result = 31 * result + Objects.hashCode(sourceName);
        return result;
    }

    @Override
    public String toString() {
        return "SourceLocation{" +
                "line=" + line +
                ", column=" + column +
                (sourceName != null ? ", sourceName=" + sourceName : "") +
                '}';
    }


    /**
     * This method can return {@link SourceLocation} that help create the given schema element.  If the
     * schema is created from input files and {@link SchemaGenerator.Options#isCaptureAstDefinitions()}
     * is set to true then schema elements contain a reference to the {@link SourceLocation} that helped
     * create that runtime schema element.
     *
     * @param schemaElement the schema element
     *
     * @return the source location if available or null if it's not.
     */
    public static @Nullable SourceLocation getLocation(GraphQLSchemaElement schemaElement) {
        if (schemaElement instanceof GraphQLModifiedType) {
            schemaElement = GraphQLTypeUtil.unwrapAllAs((GraphQLModifiedType) schemaElement);
        }
        if (schemaElement instanceof GraphQLNamedSchemaElement) {
            Node<?> node = ((GraphQLNamedSchemaElement) schemaElement).getDefinition();
            return node != null ? node.getSourceLocation() : null;
        }
        return null;
    }

}
