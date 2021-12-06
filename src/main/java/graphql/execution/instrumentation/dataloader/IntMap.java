package graphql.execution.instrumentation.dataloader;

import graphql.Internal;

import java.util.Arrays;

@Internal
public class IntMap {

    // A reasonable default that guarantees no additional allocations for most use cases.
    private static final int DEFAULT_INITIAL_SIZE = 16;

    // this array is mutable in both size and contents.
    private int[] countsByLevel;

    public IntMap(int initialSize) {
        if (initialSize < 0) {
            throw new IllegalArgumentException("negative size " + initialSize);
        }
        countsByLevel = new int[initialSize];
    }

    public IntMap() {
        this(DEFAULT_INITIAL_SIZE);
    }

    public int get(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("negative level " + level);
        }
        if (level + 1 > countsByLevel.length) {
            throw new IllegalArgumentException("unknown level " + level);
        }
        return countsByLevel[level];
    }

    public void increment(int level, int by) {
        if (level < 0) {
            throw new IllegalArgumentException("negative level " + level);
        }
        if (level + 1 > countsByLevel.length) {
            int newSize = level == 0 ? 1 : level * 2;
            countsByLevel = Arrays.copyOf(countsByLevel, newSize);
        }
        countsByLevel[level] += by;
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

    public void reset() {
        Arrays.fill(countsByLevel, 0);
    }
}