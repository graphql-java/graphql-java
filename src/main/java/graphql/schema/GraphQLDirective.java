package graphql.schema;


import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static graphql.Assert.assertNotNull;
import static graphql.introspection.Introspection.DirectiveLocation;

public class GraphQLDirective {

    private final String name;
    private final String description;
    private final EnumSet<DirectiveLocation> locations;
    private final List<GraphQLArgument> arguments = new ArrayList<GraphQLArgument>();
    private final boolean onOperation;
    private final boolean onFragment;
    private final boolean onField;

    public GraphQLDirective(String name, String description, EnumSet<DirectiveLocation> locations,
                            List<GraphQLArgument> arguments, boolean onOperation, boolean onFragment, boolean onField) {
        assertNotNull(name, "name can't be null");
        assertNotNull(arguments, "arguments can't be null");
        this.name = name;
        this.description = description;
        this.locations = locations;
        this.arguments.addAll(arguments);
        this.onOperation = onOperation;
        this.onFragment = onFragment;
        this.onField = onField;
    }

    public String getName() {
        return name;
    }

    public List<GraphQLArgument> getArguments() {
        return new ArrayList<GraphQLArgument>(arguments);
    }

    public GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }

    public EnumSet<DirectiveLocation> validLocations() {
        return locations;
    }

    /**
     * @deprecated  Use {@link #validLocations()}
     * @return onOperation
     */
    @Deprecated
    public boolean isOnOperation() {
        return onOperation;
    }

    /**
     * @deprecated  Use {@link #validLocations()}
     * @return onFragment
     */
    @Deprecated
    public boolean isOnFragment() {
        return onFragment;
    }

    /**
     * @deprecated  Use {@link #validLocations()}
     * @return onField
     */
    @Deprecated
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
        private EnumSet<DirectiveLocation> locations = EnumSet.noneOf(DirectiveLocation.class);
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

        public Builder validLocations(DirectiveLocation ...validLocations) {
            for (DirectiveLocation location : validLocations) {
                locations.add(location);
            }
            return this;
        }

        public Builder argument(GraphQLArgument fieldArgument) {
            arguments.add(fieldArgument);
            return this;
        }

        /**
         * Take an argument builder in a function definition and apply. Can be used in a jdk8 lambda
         * e.g.:
         * <pre>
         *     {@code
         *      argument(a -> a.name("argumentName"))
         *     }
         * </pre>
         *
         * @param builderFunction a supplier for the builder impl
         * @return this
         */
        public Builder argument(BuilderFunction<GraphQLArgument.Builder> builderFunction) {
            GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
            builder = builderFunction.apply(builder);
            return argument(builder);
        }

        /**
         * Same effect as the argument(GraphQLArgument). Builder.build() is called
         * from within
         *
         * @param builder an un-built/incomplete GraphQLArgument
         * @return this
         */
        public Builder argument(GraphQLArgument.Builder builder) {
            this.arguments.add(builder.build());
            return this;
        }

        /**
         * @deprecated  Use {@code graphql.schema.GraphQLDirective.Builder#validLocations(DirectiveLocation...)}
         * @param onOperation onOperation
         * @return this builder
         */
        @Deprecated
        public Builder onOperation(boolean onOperation) {
            this.onOperation = onOperation;
            return this;
        }

        /**
         * @deprecated  Use {@code graphql.schema.GraphQLDirective.Builder#validLocations(DirectiveLocation...)}
         * @param onFragment onFragment
         * @return this builder
         */
        @Deprecated
        public Builder onFragment(boolean onFragment) {
            this.onFragment = onFragment;
            return this;
        }

        /**
         * @deprecated  Use {@code graphql.schema.GraphQLDirective.Builder#validLocations(DirectiveLocation...)}
         * @param onField onField
         * @return this builder
         */
        @Deprecated
        public Builder onField(boolean onField) {
            this.onField = onField;
            return this;
        }

        public GraphQLDirective build() {
            return new GraphQLDirective(name, description, locations, arguments, onOperation, onFragment, onField);
        }


    }
}
