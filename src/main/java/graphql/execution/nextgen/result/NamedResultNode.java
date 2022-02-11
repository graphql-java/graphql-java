package graphql.execution.nextgen.result;

import graphql.Internal;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public class NamedResultNode {
    private final String name;
    private final ExecutionResultNode node;

    public NamedResultNode(String name, ExecutionResultNode node) {
        this.name = name;
        this.node = node;
    }

    public String getName() {
        return name;
    }

    public ExecutionResultNode getNode() {
        return node;
    }

    public NamedResultNode withNode(ExecutionResultNode newNode) {
        return new NamedResultNode(name, newNode);
    }
}
