package graphql.validation;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

import java.util.List;

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
    private final List<String> introspectionFieldKeys;

    FragmentComplexityInfo(int fieldCount, int maxDepth, List<String> introspectionFieldKeys) {
        this.fieldCount = fieldCount;
        this.maxDepth = maxDepth;
        this.introspectionFieldKeys = introspectionFieldKeys;
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

    /**
     * @return the good faith introspection field keys counted in this fragment, including keys from nested fragments
     */
    List<String> getIntrospectionFieldKeys() {
        return introspectionFieldKeys;
    }

    @Override
    public String toString() {
        return "FragmentComplexityInfo{" +
                "fieldCount=" + fieldCount +
                ", maxDepth=" + maxDepth +
                ", introspectionFieldKeys=" + introspectionFieldKeys +
                '}';
    }
}
