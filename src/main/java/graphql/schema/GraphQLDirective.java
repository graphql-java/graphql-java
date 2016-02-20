package graphql.schema;


import java.util.ArrayList;
import java.util.List;

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLDirective class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLDirective {

    private final String name;
    private final String description;
    private final List<GraphQLArgument> arguments = new ArrayList<GraphQLArgument>();
    private final boolean onOperation;
    private final boolean onFragment;
    private final boolean onField;

    /**
     * <p>Constructor for GraphQLDirective.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param arguments a {@link java.util.List} object.
     * @param onOperation a boolean.
     * @param onFragment a boolean.
     * @param onField a boolean.
     */
    public GraphQLDirective(String name, String description, List<GraphQLArgument> arguments, boolean onOperation, boolean onFragment, boolean onField) {
        assertNotNull(name, "name can't be null");
        assertNotNull(arguments, "arguments can't be null");
        this.name = name;
        this.description = description;
        this.arguments.addAll(arguments);
        this.onOperation = onOperation;
        this.onFragment = onFragment;
        this.onField = onField;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Getter for the field <code>arguments</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLArgument> getArguments() {
        return new ArrayList<GraphQLArgument>(arguments);
    }

    /**
     * <p>getArgument.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @return a {@link graphql.schema.GraphQLArgument} object.
     */
    public GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }

    /**
     * <p>isOnOperation.</p>
     *
     * @return a boolean.
     */
    public boolean isOnOperation() {
        return onOperation;
    }

    /**
     * <p>isOnFragment.</p>
     *
     * @return a boolean.
     */
    public boolean isOnFragment() {
        return onFragment;
    }

    /**
     * <p>isOnField.</p>
     *
     * @return a boolean.
     */
    public boolean isOnField() {
        return onField;
    }

    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDescription() {
        return description;
    }

    /**
     * <p>newDirective.</p>
     *
     * @return a {@link graphql.schema.GraphQLDirective.Builder} object.
     */
    public static Builder newDirective() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private final List<GraphQLArgument> arguments = new ArrayList<GraphQLArgument>();
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

        public Builder argument(GraphQLArgument fieldArgument) {
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
