package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Identifies the parent and element kinds for a schema-element comparator.
 */
@ExperimentalApi
@NullMarked
public final class SchemaElementComparatorEnvironment {

    private final @Nullable Class<? extends SchemaElement> parentType;
    private final @Nullable Class<? extends SchemaElement> elementType;

    private SchemaElementComparatorEnvironment(
            @Nullable Class<? extends SchemaElement> parentType,
            @Nullable Class<? extends SchemaElement> elementType) {
        this.parentType = parentType;
        this.elementType = elementType;
    }

    /**
     * Creates an environment describing the semantic schema kinds being sorted.
     *
     * @param parentType the containing element kind, or {@code null}
     * @param elementType the element kind, or {@code null} for top-level elements
     *
     * @return a comparator environment
     */
    public static SchemaElementComparatorEnvironment newEnvironment(
            @Nullable Class<? extends SchemaElement> parentType,
            @Nullable Class<? extends SchemaElement> elementType) {
        return new SchemaElementComparatorEnvironment(
                parentType,
                elementType);
    }

    /**
     * @return the containing element kind, or {@code null}
     */
    public @Nullable Class<? extends SchemaElement> getParentType() {
        return parentType;
    }

    /**
     * @return the element kind, or {@code null} for top-level elements
     */
    public @Nullable Class<? extends SchemaElement> getElementType() {
        return elementType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SchemaElementComparatorEnvironment)) {
            return false;
        }
        SchemaElementComparatorEnvironment that =
                (SchemaElementComparatorEnvironment) other;
        return Objects.equals(parentType, that.parentType)
                && Objects.equals(elementType, that.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentType, elementType);
    }
}
