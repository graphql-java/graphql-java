package graphql.util;

import graphql.Internal;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Tracks a selection-set cursor and its context for iterative fragment expansion.
 * {@link graphql.execution.FieldCollector} carries the deferred-execution context;
 * {@link graphql.validation.OperationValidator} carries the parent type for field-merge validation.
 * Both use these frames on explicit stacks to preserve selection order and context
 * without growing the Java call stack with fragment indirection.
 */
@Internal
@NullMarked
public final class SelectionSetTraversalFrame<T> {
    private final List<Selection> selections;
    private final @Nullable T context;
    private int nextSelection;

    public SelectionSetTraversalFrame(SelectionSet selectionSet, @Nullable T context) {
        this.selections = selectionSet.getSelections();
        this.context = context;
    }

    public @Nullable Selection<?> nextSelection() {
        if (nextSelection == selections.size()) {
            return null;
        }
        return selections.get(nextSelection++);
    }

    public @Nullable T getContext() {
        return context;
    }
}
