package graphql.schema;


import java.util.ArrayList;
import java.util.List;

public class GraphQLDirective {

    private final String name;
    private final List<GraphQLFieldArgument> arguments = new ArrayList<>();
    private final boolean onOperation;
    private final boolean onFragment;
    private final boolean onField;

    public GraphQLDirective(String name, List<GraphQLFieldArgument> arguments, boolean onOperation, boolean onFragment, boolean onField) {
        this.name = name;
        this.arguments.addAll(arguments);
        this.onOperation = onOperation;
        this.onFragment = onFragment;
        this.onField = onField;
    }

    public String getName() {
        return name;
    }

    public List<GraphQLFieldArgument> getArguments() {
        return arguments;
    }

    public boolean isOnOperation() {
        return onOperation;
    }

    public boolean isOnFragment() {
        return onFragment;
    }

    public boolean isOnField() {
        return onField;
    }
}
