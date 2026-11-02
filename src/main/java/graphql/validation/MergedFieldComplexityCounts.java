package graphql.validation;

import graphql.Internal;
import graphql.introspection.GoodFaithIntrospection;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Summarizes merged validation fields: their count, relative field depth, and restricted
 * introspection coordinates. General complexity limits and Good Faith introspection share this summary.
 * Fields grouped by response name and common parent type count once at each response position;
 * their merged children also contribute. A cached summary contributes again at each position where it is used.
 *
 * <p>For example, {@code { node { name name } }} has two merged fields and a field depth of two.
 * Its three expanded field occurrences are counted separately by {@link OperationValidator}
 * for {@link QueryComplexityLimits#getMaxFieldOccurrences()}.
 */
@Internal
@NullMarked
public final class MergedFieldComplexityCounts {
    private final int maxMergedFieldsCount;
    private final int maxDepth;
    private final boolean introspection;
    private long mergedFieldCount;
    private int depth;
    private @Nullable Set<String> coordinates;

    public MergedFieldComplexityCounts(QueryComplexityLimits limits, boolean introspection) {
        this.introspection = introspection;
        maxMergedFieldsCount = introspection ? Math.min(limits.getMaxFieldsCount(), GoodFaithIntrospection.GOOD_FAITH_MAX_FIELDS_COUNT) : limits.getMaxFieldsCount();
        maxDepth = introspection ? Math.min(limits.getMaxDepth(), GoodFaithIntrospection.GOOD_FAITH_MAX_DEPTH_COUNT) : limits.getMaxDepth();
    }

    public void enterField(@Nullable String coordinate) {
        mergedFieldCount = 1;
        depth = 1;
        if (coordinate != null) {
            coordinates = new LinkedHashSet<>();
            coordinates.add(coordinate);
        }
    }

    public void checkFieldDepth(int parentDepth) {
        checkDepth(parentDepth, 1);
    }

    public void addLeafField(@Nullable String coordinate) {
        mergedFieldCount++;
        depth = Math.max(depth, 1);
        if (coordinate != null) {
            addCoordinate(coordinate);
        }
        checkLimits();
    }

    public void add(MergedFieldComplexityCounts other) {
        mergedFieldCount += other.mergedFieldCount;
        depth = Math.max(depth, other.depth);
        addCoordinates(other);
        checkLimits();
    }

    public void addChildren(MergedFieldComplexityCounts children) {
        mergedFieldCount += children.mergedFieldCount;
        depth = children.depth + 1;
        addCoordinates(children);
        checkLimits();
    }

    private void addCoordinates(MergedFieldComplexityCounts other) {
        if (other.coordinates == null) {
            return;
        }
        if (coordinates == null) {
            coordinates = new LinkedHashSet<>(other.coordinates);
            return;
        }
        for (String coordinate : other.coordinates) {
            addCoordinate(coordinate);
        }
    }

    private void addCoordinate(String coordinate) {
        if (coordinates == null) {
            coordinates = new LinkedHashSet<>();
        }
        if (!coordinates.add(coordinate)) {
            throw GoodFaithIntrospectionExceeded.tooManyFields(coordinate);
        }
    }

    private void checkLimits() {
        if (mergedFieldCount > maxMergedFieldsCount) {
            if (introspection) {
                throw GoodFaithIntrospectionExceeded.tooBigOperation("Query has " + mergedFieldCount + " fields which exceeds maximum allowed " + maxMergedFieldsCount);
            }
            throw new QueryComplexityLimitsExceeded(ValidationErrorType.MaxQueryFieldsExceeded, maxMergedFieldsCount, mergedFieldCount);
        }
        checkDepth(0);
    }

    public void checkDepth(int parentDepth) {
        checkDepth(parentDepth, depth);
    }

    private void checkDepth(int parentDepth, int selectionDepth) {
        long actualDepth = (long) parentDepth + selectionDepth;
        if (actualDepth <= maxDepth) {
            return;
        }
        if (introspection) {
            throw GoodFaithIntrospectionExceeded.tooBigOperation("Query depth " + actualDepth + " exceeds maximum allowed depth " + maxDepth);
        }
        throw new QueryComplexityLimitsExceeded(ValidationErrorType.MaxQueryDepthExceeded, maxDepth, actualDepth);
    }
}
