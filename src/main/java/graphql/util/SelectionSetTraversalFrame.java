package graphql.util;

import graphql.Internal;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

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
