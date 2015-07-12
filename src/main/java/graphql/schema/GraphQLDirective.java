package graphql.schema;


import java.util.ArrayList;
import java.util.List;

public class GraphQLDirective {

    private final String name;
    private final String description;
    private final List<GraphQLFieldArgument> arguments = new ArrayList<>();
    private final boolean onOperation;
    private final boolean onFragment;
    private final boolean onField;

    public GraphQLDirective(String name, String description, List<GraphQLFieldArgument> arguments, boolean onOperation, boolean onFragment, boolean onField) {
        this.name = name;
        this.description = description;
        this.arguments.addAll(arguments);
        this.onOperation = onOperation;
        this.onFragment = onFragment;
        this.onField = onField;
    }

    public String getName() {
        return name;
    }

    public List<GraphQLFieldArgument> getArguments() {
        return new ArrayList<>(arguments);
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

    public String getDescription() {
        return description;
    }

    public static Builder newDirective() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private final List<GraphQLFieldArgument> arguments = new ArrayList<>();
        private String description;
        private boolean onOperation;
        private boolean onFragment;
        private boolean onField;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder argument(GraphQLFieldArgument fieldArgument) {
            arguments.add(fieldArgument);
            return this;
        }

        public Builder onOperation(boolean onOperation) {
            this.onOperation = onOperation;
            return this;
        }

        public Builder onFragment(boolean onFragment) {
            this.onFragment = onFragment;
            return this;
        }

        public Builder onField(boolean onField) {
            this.onField = onField;
            return this;
        }

        public GraphQLDirective build() {
            return new GraphQLDirective(name, description, arguments, onOperation, onFragment, onField);
        }


    }
}
