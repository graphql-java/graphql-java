package graphql.validation;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

@Internal
@NullMarked
public final class FieldValidationState<T> {
    // Marked before recursion to stop cycles; a checked set can still have conflicts.
    private boolean sameResponseShapeChecked;
    private boolean sameForCommonParentsChecked;
    private @Nullable Map<String, Set<T>> mergedSubSelections;

    public boolean markResponseShapeChecked() {
        boolean newlyChecked = !sameResponseShapeChecked;
        sameResponseShapeChecked = true;
        return newlyChecked;
    }

    public boolean markSameForCommonParentsChecked() {
        boolean newlyChecked = !sameForCommonParentsChecked;
        sameForCommonParentsChecked = true;
        return newlyChecked;
    }

    public @Nullable Map<String, Set<T>> getMergedSubSelections() {
        return mergedSubSelections;
    }

    public void setMergedSubSelections(Map<String, Set<T>> mergedSubSelections) {
        this.mergedSubSelections = mergedSubSelections;
    }
}
