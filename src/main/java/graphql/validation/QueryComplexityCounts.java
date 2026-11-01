package graphql.validation;

import graphql.Internal;
import graphql.introspection.GoodFaithIntrospection;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

@Internal
@NullMarked
public final class QueryComplexityCounts {
    private final int maxFieldsCount;
    private final int maxDepth;
    private final boolean introspection;
    private long fieldCount;
    private int depth;
    private @Nullable Set<String> coordinates;

    public QueryComplexityCounts(QueryComplexityLimits limits, boolean introspection) {
        this.introspection = introspection;
        maxFieldsCount = introspection ? Math.min(limits.getMaxFieldsCount(), GoodFaithIntrospection.GOOD_FAITH_MAX_FIELDS_COUNT) : limits.getMaxFieldsCount();
        maxDepth = introspection ? Math.min(limits.getMaxDepth(), GoodFaithIntrospection.GOOD_FAITH_MAX_DEPTH_COUNT) : limits.getMaxDepth();
    }

    public void enterField(@Nullable String coordinate, int parentDepth) {
        fieldCount = 1;
        depth = 1;
        if (coordinate != null) {
            coordinates = new LinkedHashSet<>();
            coordinates.add(coordinate);
        }
        checkDepth(parentDepth);
    }

    public void add(QueryComplexityCounts other) {
        fieldCount += other.fieldCount;
        depth = Math.max(depth, other.depth);
        addCoordinates(other);
        checkLimits();
    }

    public void addChildren(QueryComplexityCounts children) {
        fieldCount += children.fieldCount;
        depth = children.depth + 1;
        addCoordinates(children);
        checkLimits();
    }

    private void addCoordinates(QueryComplexityCounts other) {
        if (other.coordinates == null) {
            return;
        }
        if (coordinates == null) {
            coordinates = new LinkedHashSet<>(other.coordinates);
            return;
        }
        for (String coordinate : other.coordinates) {
            if (!coordinates.add(coordinate)) {
                throw GoodFaithIntrospectionExceeded.tooManyFields(coordinate);
            }
        }
    }

    private void checkLimits() {
        if (fieldCount > maxFieldsCount) {
            if (introspection) {
                throw GoodFaithIntrospectionExceeded.tooBigOperation("Query has " + fieldCount + " fields which exceeds maximum allowed " + maxFieldsCount);
            }
            throw new QueryComplexityLimitsExceeded(ValidationErrorType.MaxQueryFieldsExceeded, maxFieldsCount, fieldCount);
        }
        checkDepth(0);
    }

    public void checkDepth(int parentDepth) {
        long actualDepth = (long) parentDepth + depth;
        if (actualDepth <= maxDepth) {
            return;
        }
        if (introspection) {
            throw GoodFaithIntrospectionExceeded.tooBigOperation("Query depth " + actualDepth + " exceeds maximum allowed depth " + maxDepth);
        }
        throw new QueryComplexityLimitsExceeded(ValidationErrorType.MaxQueryDepthExceeded, maxDepth, actualDepth);
    }
}
