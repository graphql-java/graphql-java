package graphql.normalized;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.io.Serializable;
import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * An ordered selection set. Fragment references are retained instead of expanded.
 */
@PublicApi
@NullMarked
public final class NormalizedSelectionSet implements Serializable {

    private final List<NormalizedSelection> selections;

    private NormalizedSelectionSet(Builder builder) {
        this.selections = List.copyOf(builder.selections);
    }

    /** @return the fields and scopes in source order */
    public List<NormalizedSelection> getSelections() {
        return selections;
    }

    public static Builder newNormalizedSelectionSet() {
        return new Builder();
    }

    public NormalizedSelectionSet transform(Consumer<Builder> consumer) {
        Builder builder = new Builder();
        builder.selections = selections;
        consumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static class Builder {
        private List<NormalizedSelection> selections = List.of();

        public Builder selections(List<NormalizedSelection> selections) {
            this.selections = selections;
            return this;
        }

        public NormalizedSelectionSet build() {
            return new NormalizedSelectionSet(this);
        }
    }
}
