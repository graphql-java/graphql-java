package graphql.validation;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * Holds pre-calculated complexity metrics for a fragment definition.
 * This is used to efficiently track query complexity when fragments are spread
 * at multiple locations in a query.
 */
@Internal
@NullMarked
class FragmentComplexityInfo {

    private final int fieldCount;
    private final int maxDepth;

    FragmentComplexityInfo(int fieldCount, int maxDepth) {
        this.fieldCount = fieldCount;
        this.maxDepth = maxDepth;
    }

    /**
     * @return the total number of fields in this fragment, including fields from nested fragments
     */
    int getFieldCount() {
        return fieldCount;
    }

    /**
     * @return the maximum depth of fields within this fragment
     */
    int getMaxDepth() {
        return maxDepth;
    }

    @Override
    public String toString() {
        return "FragmentComplexityInfo{" +
                "fieldCount=" + fieldCount +
                ", maxDepth=" + maxDepth +
                '}';
    }
}
