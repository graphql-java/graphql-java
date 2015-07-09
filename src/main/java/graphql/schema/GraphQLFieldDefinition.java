package graphql.schema;


import graphql.language.Argument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphQLFieldDefinition {

    private final String name;
    private final String description;
    private final GraphQLOutputType type;
    private final ResolveValue resolveValue;
    private final List<GraphQLFieldArgument> arguments = new ArrayList<>();


    public GraphQLFieldDefinition(String name, String description, GraphQLOutputType type, ResolveValue resolveValue, List<GraphQLFieldArgument> arguments) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.resolveValue = resolveValue;
        if (arguments != null) {
            this.arguments.addAll(arguments);
        }
    }


    public String getName() {
        return name;
    }


    public GraphQLOutputType getType() {
        return type;
    }

    public ResolveValue getResolveValue() {
        return resolveValue;
    }

    public List<GraphQLFieldArgument> getArguments() {
        return arguments;
    }

    public String getDescription() {
        return description;
    }

    public static Builder newFieldDefinition() {
        return new Builder();
    }

    public static class Builder {
        private ResolveValue defaultResolver = new ResolveValue() {
            @Override
            public Object resolve(Object source, List<Object> arguments) {
                return ((Map<String, Object>) source).get(Builder.this.name);
            }
        };

        private String name;
        private String description;
        private GraphQLOutputType type;
        private ResolveValue resolveValue = defaultResolver;
        private List<GraphQLFieldArgument> arguments = new ArrayList<>();


        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder type(GraphQLOutputType type) {
            this.type = type;
            return this;
        }

        public Builder resolveValue(ResolveValue resolveValue) {
            this.resolveValue = resolveValue;
            return this;
        }

        public Builder staticValue(final Object value) {
            this.resolveValue = new ResolveValue() {
                @Override
                public Object resolve(Object source, List<Object> arguments) {
                    return value;
                }
            };
            return this;
        }

        public Builder argument(GraphQLFieldArgument argument) {
            this.arguments.add(argument);
            return this;
        }

        public Builder argument(List<GraphQLFieldArgument> arguments) {
            this.arguments.addAll(arguments);
            return this;
        }

        public GraphQLFieldDefinition build() {
            return new GraphQLFieldDefinition(name, description, type, resolveValue, arguments);
        }


    }
}
