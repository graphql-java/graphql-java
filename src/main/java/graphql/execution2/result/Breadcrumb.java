package graphql.execution2.result;

public class Breadcrumb {
    public final ExecutionResultNode node;
    public final ExecutionResultNodePosition position;

    public Breadcrumb(ExecutionResultNode node, ExecutionResultNodePosition position) {
        this.node = node;
        this.position = position;
    }

    @Override
    public String toString() {
        return "Breadcrumb{" +
                "node=" + node +
                ", position=" + position +
                '}';
    }
}