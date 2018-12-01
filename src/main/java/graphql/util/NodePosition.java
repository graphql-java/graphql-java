package graphql.util;

import graphql.PublicApi;

/**
 * General position of a Node inside a parent.
 *
 * Can be an index or a name with an index.
 */
@PublicApi
public class NodePosition {

    private final String name;
    private final int index;

    public NodePosition(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        return "NodePosition{" +
                "name='" + name + '\'' +
                ", index=" + index +
                '}';
    }
}
