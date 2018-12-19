package graphql.execution.conversion;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputType;

@Internal
public class ArgumentConverterEnvironmentImpl implements ArgumentConverterEnvironment {

    private final GraphQLArgument argument;
    private final Object sourceObject;

    private ArgumentConverterEnvironmentImpl(GraphQLArgument argument, Object sourceObject) {
        this.argument = argument;
        this.sourceObject = sourceObject;
    }


    @Override
    public GraphQLArgument getArgument() {
        return argument;
    }

    @Override
    public GraphQLInputType getArgumentType() {
        return argument.getType();
    }

    @Override
    public Object getValueToBeConverted() {
        return sourceObject;
    }


    public static Builder newEnvironment() {
        return new Builder();
    }

    public static class Builder {
        private GraphQLArgument argument;
        private Object sourceObject;


        public Builder argument(GraphQLArgument argument) {
            this.argument = argument;
            return this;
        }

        public Builder sourceObject(Object sourceObject) {
            this.sourceObject = sourceObject;
            return this;
        }

        public ArgumentConverterEnvironmentImpl build() {
            return new ArgumentConverterEnvironmentImpl(argument, sourceObject);
        }


    }
}
