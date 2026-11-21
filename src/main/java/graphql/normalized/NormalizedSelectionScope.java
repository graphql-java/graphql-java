package graphql.normalized;

import graphql.PublicApi;
import graphql.language.DirectivesContainer;
import graphql.language.FragmentDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * An inline fragment or a reference to a shared named fragment body.
 * The source directives belong to this reference: two spreads of the same fragment
 * can have different conditions. Enclosing scopes and this scope must all be included.
 */
@PublicApi
@NullMarked
public final class NormalizedSelectionScope implements NormalizedSelection {

    private final DirectivesContainer<?> source;
    private final @Nullable String typeCondition;
    private final @Nullable FragmentDefinition fragmentDefinition;
    private final int selectionSetId;

    private NormalizedSelectionScope(Builder builder) {
        this.source = assertNotNull(builder.source);
        this.typeCondition = builder.typeCondition;
        this.fragmentDefinition = builder.fragmentDefinition;
        this.selectionSetId = builder.selectionSetId;
    }

    /** @return the original spread or inline fragment with its symbolic conditions */
    public DirectivesContainer<?> getSource() {
        return source;
    }

    /** @return the required type, or null for an inline fragment without a type condition */
    public @Nullable String getTypeCondition() {
        return typeCondition;
    }

    /** @return the shared fragment definition, or null for an inline fragment */
    public @Nullable FragmentDefinition getFragmentDefinition() {
        return fragmentDefinition;
    }

    /** @return the referenced selection set ID */
    public int getSelectionSetId() {
        return selectionSetId;
    }

    public static Builder newNormalizedSelectionScope() {
        return new Builder();
    }

    public NormalizedSelectionScope transform(Consumer<Builder> consumer) {
        Builder builder = new Builder();
        builder.source = source;
        builder.typeCondition = typeCondition;
        builder.fragmentDefinition = fragmentDefinition;
        builder.selectionSetId = selectionSetId;
        consumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static class Builder {
        private DirectivesContainer<?> source;
        private @Nullable String typeCondition;
        private @Nullable FragmentDefinition fragmentDefinition;
        private int selectionSetId;

        public Builder source(DirectivesContainer<?> source) {
            this.source = source;
            return this;
        }

        public Builder typeCondition(@Nullable String typeCondition) {
            this.typeCondition = typeCondition;
            return this;
        }

        public Builder fragmentDefinition(@Nullable FragmentDefinition fragmentDefinition) {
            this.fragmentDefinition = fragmentDefinition;
            return this;
        }

        public Builder selectionSetId(int selectionSetId) {
            this.selectionSetId = selectionSetId;
            return this;
        }

        public NormalizedSelectionScope build() {
            return new NormalizedSelectionScope(this);
        }
    }
}
