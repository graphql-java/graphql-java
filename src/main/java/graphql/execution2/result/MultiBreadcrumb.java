package graphql.execution2.result;

import java.util.List;

public class MultiBreadcrumb {
    public final ExecutionResultNode node;
    public final List<ExecutionResultNodePosition> positions;

    public MultiBreadcrumb(ExecutionResultNode node, List<ExecutionResultNodePosition> positions) {
        this.node = node;
        this.positions = positions;
    }

    @Override
    public String toString() {
        return "Breadcrumb{" +
                "node=" + node +
                ", positions=" + positions +
                '}';
    }
}