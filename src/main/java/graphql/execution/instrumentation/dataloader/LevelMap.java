package graphql.execution.instrumentation.dataloader;

import graphql.Internal;

import java.util.Arrays;

/**
 * This data structure tracks the number of expected calls on a given level
 */
@Internal
public class LevelMap {

    // A reasonable default that guarantees no additional allocations for most use cases.
    private static final int DEFAULT_INITIAL_SIZE = 16;

    // this array is mutable in both size and contents.
    private int[] countsByLevel;

    public LevelMap(int initialSize) {
        if (initialSize < 0) {
            throw new IllegalArgumentException("negative size " + initialSize);
        }
        countsByLevel = new int[initialSize];
    }

    public LevelMap() {
        this(DEFAULT_INITIAL_SIZE);
    }

    public int get(int level) {
        maybeResize(level);
        return countsByLevel[level];
    }

    public void increment(int level, int by) {
        maybeResize(level);
        countsByLevel[level] += by;
    }

    public void set(int level, int newValue) {
        maybeResize(level);
        countsByLevel[level] = newValue;
    }

    private void maybeResize(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("negative level " + level);
        }
        if (level + 1 > countsByLevel.length) {
            int newSize = level == 0 ? 1 : level * 2;
            countsByLevel = Arrays.copyOf(countsByLevel, newSize);
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("IntMap[");
        for (int i = 0; i < countsByLevel.length; i++) {
            result.append("level=").append(i).append(",count=").append(countsByLevel[i]).append(" ");
        }
        result.append("]");
        return result.toString();
    }

    public String toString(int level) {
        StringBuilder result = new StringBuilder();
        result.append("IntMap[");
        for (int i = 1; i <= level; i++) {
            result.append("level=").append(i).append(",count=").append(countsByLevel[i]).append(" ");
        }
        result.append("]");
        return result.toString();
    }

    public void clear() {
        Arrays.fill(countsByLevel, 0);
    }
}
