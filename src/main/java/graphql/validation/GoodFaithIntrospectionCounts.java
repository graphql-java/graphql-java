package graphql.validation;

import graphql.Internal;
import graphql.introspection.GoodFaithIntrospection;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

@Internal
@NullMarked
public final class GoodFaithIntrospectionCounts {
    private int fieldCount;
    private int depth;
    private @Nullable Set<String> coordinates;

    public void enterField(@Nullable String coordinate, int parentDepth) {
        fieldCount = 1;
        depth = 1;
        if (coordinate != null) {
            coordinates = new LinkedHashSet<>();
            coordinates.add(coordinate);
        }
        checkDepth(parentDepth);
    }

    public void add(GoodFaithIntrospectionCounts other) {
        fieldCount += other.fieldCount;
        depth = Math.max(depth, other.depth);
        addCoordinates(other);
        checkLimits();
    }

    public void addChildren(GoodFaithIntrospectionCounts children) {
        fieldCount += children.fieldCount;
        depth = children.depth + 1;
        addCoordinates(children);
        checkLimits();
    }

    private void addCoordinates(GoodFaithIntrospectionCounts other) {
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
        if (fieldCount > GoodFaithIntrospection.GOOD_FAITH_MAX_FIELDS_COUNT) {
            throw GoodFaithIntrospectionExceeded.tooBigOperation("Query has " + fieldCount + " fields which exceeds maximum allowed "
                    + GoodFaithIntrospection.GOOD_FAITH_MAX_FIELDS_COUNT);
        }
        checkDepth(0);
    }

    public void checkDepth(int parentDepth) {
        if (parentDepth + depth > GoodFaithIntrospection.GOOD_FAITH_MAX_DEPTH_COUNT) {
            throw GoodFaithIntrospectionExceeded.tooBigOperation("Query depth " + (parentDepth + depth) + " exceeds maximum allowed depth "
                    + GoodFaithIntrospection.GOOD_FAITH_MAX_DEPTH_COUNT);
        }
    }
}
