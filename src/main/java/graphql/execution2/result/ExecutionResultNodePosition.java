package graphql.execution2.result;

import graphql.Assert;

public class ExecutionResultNodePosition {

    private final Integer index;
    private final String key;

    public ExecutionResultNodePosition(Integer index) {
        this.index = index;
        this.key = null;
    }

    public ExecutionResultNodePosition(String key) {
        this.index = null;
        this.key = key;
    }

    public static ExecutionResultNodePosition index(int index) {
        return new ExecutionResultNodePosition(index);
    }

    public static ExecutionResultNodePosition key(String key) {
        return new ExecutionResultNodePosition(key);
    }

    public int getIndex() {
        return Assert.assertNotNull(index);
    }

    public String getKey() {
        return Assert.assertNotNull(key);
    }

    @Override
    public String toString() {
        return index != null ? index.toString() : key;
    }

}
