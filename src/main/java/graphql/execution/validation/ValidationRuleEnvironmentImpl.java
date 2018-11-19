package graphql.execution.validation;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

@Internal
class ValidationRuleEnvironmentImpl implements ValidationRuleEnvironment {

    private final DataFetchingEnvironment dataFetchingEnvironment;
    private final GraphQLArgument validatedArgument;
    private final Map<Object, Object> context;

    private ValidationRuleEnvironmentImpl(DataFetchingEnvironment dataFetchingEnvironment, GraphQLArgument validatedArgument, Map<Object, Object> context) {
        this.dataFetchingEnvironment = dataFetchingEnvironment;
        this.validatedArgument = validatedArgument;
        this.context = context;
    }

    @Override
    public DataFetchingEnvironment getDataFetchingEnvironment() {
        return dataFetchingEnvironment;
    }

    @Override
    public GraphQLFieldDefinition getValidatedField() {
        return dataFetchingEnvironment.getFieldDefinition();
    }

    @Override
    public GraphQLArgument getValidatedArgument() {
        return validatedArgument;
    }

    @Override
    public Object getValidatedArgumentValue() {
        if (validatedArgument != null) {
            return dataFetchingEnvironment.getArgument(validatedArgument.getName());
        } else {
            return null;
        }
    }

    @Override
    public Map<String, Object> getArguments() {
        return dataFetchingEnvironment.getArguments();
    }

    @Override
    public boolean containsArgument(String name) {
        return dataFetchingEnvironment.containsArgument(name);
    }

    @Override
    public <T> T getArgument(String name) {
        return dataFetchingEnvironment.getArgument(name);
    }

    @Override
    public Map<Object, Object> getPerFieldContext() {
        return context;
    }

    @Override
    public GraphQLError mkError(String message) {
        return new ValidationExecutionError(message,
                dataFetchingEnvironment.getExecutionStepInfo().getPath(), dataFetchingEnvironment.getField()
        );
    }

    static Builder newRuleEnvironment() {
        return new Builder();
    }

    public static class Builder {
        private DataFetchingEnvironment dataFetchingEnvironment;
        private GraphQLArgument validatedArgument;
        private Map<Object, Object> context = new LinkedHashMap<>();

        public Builder dataFetchingEnvironment(DataFetchingEnvironment dataFetchingEnvironment) {
            this.dataFetchingEnvironment = dataFetchingEnvironment;
            return this;
        }

        public Builder context(Map<Object, Object> context) {
            this.context = context;
            return this;
        }

        public Builder validatedArgument(GraphQLArgument validatedArgument) {
            this.validatedArgument = validatedArgument;
            return this;
        }

        public ValidationRuleEnvironment build() {
            return new ValidationRuleEnvironmentImpl(dataFetchingEnvironment, validatedArgument, context);
        }
    }
}
