package graphql.execution;

import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;

import java.util.Map;

/**
 * The parameters available to {@link DataFetcherExceptionHandler}s
 */
public class DataFetcherExceptionHandlerParameters {

    private final ExecutionContext executionContext;
    private final DataFetchingEnvironment dataFetchingEnvironment;
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final Map<String, Object> argumentValues;
    private final ExecutionPath path;
    private final Throwable exception;

    public DataFetcherExceptionHandlerParameters(ExecutionContext executionContext, DataFetchingEnvironment dataFetchingEnvironment, Field field, GraphQLFieldDefinition fieldDefinition, Map<String, Object> argumentValues, ExecutionPath path, Throwable exception) {
        this.executionContext = executionContext;
        this.dataFetchingEnvironment = dataFetchingEnvironment;
        this.field = field;
        this.fieldDefinition = fieldDefinition;
        this.argumentValues = argumentValues;
        this.path = path;
        this.exception = exception;
    }

    public static Builder newExceptionParameters() {
        return new Builder();
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public DataFetchingEnvironment getDataFetchingEnvironment() {
        return dataFetchingEnvironment;
    }

    public Field getField() {
        return field;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public Map<String, Object> getArgumentValues() {
        return argumentValues;
    }

    public ExecutionPath getPath() {
        return path;
    }

    public Throwable getException() {
        return exception;
    }

    public static class Builder {
        ExecutionContext executionContext;
        DataFetchingEnvironment dataFetchingEnvironment;
        Field field;
        GraphQLFieldDefinition fieldDefinition;
        Map<String, Object> argumentValues;
        ExecutionPath path;
        Throwable exception;

        private Builder() {
        }

        public Builder executionContext(ExecutionContext executionContext) {
            this.executionContext = executionContext;
            return this;
        }

        public Builder dataFetchingEnvironment(DataFetchingEnvironment dataFetchingEnvironment) {
            this.dataFetchingEnvironment = dataFetchingEnvironment;
            return this;
        }

        public Builder field(Field field) {
            this.field = field;
            return this;
        }

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder argumentValues(Map<String, Object> argumentValues) {
            this.argumentValues = argumentValues;
            return this;
        }

        public Builder path(ExecutionPath path) {
            this.path = path;
            return this;
        }

        public Builder exception(Throwable exception) {
            this.exception = exception;
            return this;
        }

        public DataFetcherExceptionHandlerParameters build() {
            return new DataFetcherExceptionHandlerParameters(executionContext, dataFetchingEnvironment, field, fieldDefinition, argumentValues, path, exception);
        }
    }
}
