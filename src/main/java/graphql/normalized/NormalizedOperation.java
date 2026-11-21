package graphql.normalized;

import graphql.PublicApi;
import graphql.language.OperationDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.io.Serializable;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * A validated operation and the root of its shared selection graph.
 */
@PublicApi
@NullMarked
public final class NormalizedOperation implements Serializable {

    private final OperationDefinition source;
    private final int selectionSetId;

    private NormalizedOperation(Builder builder) {
        this.source = assertNotNull(builder.source);
        this.selectionSetId = builder.selectionSetId;
    }

    /** @return the operation, including its variable definitions and directives */
    public OperationDefinition getSource() {
        return source;
    }

    /** @return the root selection set ID */
    public int getSelectionSetId() {
        return selectionSetId;
    }

    public static Builder newNormalizedOperation() {
        return new Builder();
    }

    public NormalizedOperation transform(Consumer<Builder> consumer) {
        Builder builder = new Builder();
        builder.source = source;
        builder.selectionSetId = selectionSetId;
        consumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static class Builder {
        private OperationDefinition source;
        private int selectionSetId;

        public Builder source(OperationDefinition source) {
            this.source = source;
            return this;
        }

        public Builder selectionSetId(int selectionSetId) {
            this.selectionSetId = selectionSetId;
            return this;
        }

        public NormalizedOperation build() {
            return new NormalizedOperation(this);
        }
    }
}
