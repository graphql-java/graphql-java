package graphql.normalized;

import graphql.PublicApi;
import graphql.language.Field;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * One field AST node in the shared graph. Each reference to its fragment can
 * contribute a logical occurrence without copying this node.
 * Arguments and directives remain AST values; they are not coerced during validation.
 */
@PublicApi
@NullMarked
public final class NormalizedField implements NormalizedSelection {

    private final Field source;
    private final String parentTypeName;
    private final int selectionSetId;

    private NormalizedField(Builder builder) {
        this.source = assertNotNull(builder.source);
        this.parentTypeName = assertNotNull(builder.parentTypeName);
        this.selectionSetId = builder.selectionSetId;
    }

    /** @return the original field, including its alias, arguments, directives and location */
    public Field getSource() {
        return source;
    }

    /** @return the declared parent type at this AST location */
    public String getParentTypeName() {
        return parentTypeName;
    }

    /** @return the child selection set ID, or -1 for a leaf */
    public int getSelectionSetId() {
        return selectionSetId;
    }

    public static Builder newNormalizedField() {
        return new Builder();
    }

    public NormalizedField transform(Consumer<Builder> consumer) {
        Builder builder = new Builder();
        builder.source = source;
        builder.parentTypeName = parentTypeName;
        builder.selectionSetId = selectionSetId;
        consumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static class Builder {
        private Field source;
        private String parentTypeName;
        private int selectionSetId = -1;

        public Builder source(Field source) {
            this.source = source;
            return this;
        }

        public Builder parentTypeName(String parentTypeName) {
            this.parentTypeName = parentTypeName;
            return this;
        }

        public Builder selectionSetId(int selectionSetId) {
            this.selectionSetId = selectionSetId;
            return this;
        }

        public NormalizedField build() {
            return new NormalizedField(this);
        }
    }
}
