package graphql.execution;

import java.util.List;
import java.util.Map;

import graphql.language.Field;
import graphql.schema.GraphQLType;

class ValueCompletionParameters {

    private final ExecutionContext executionContext;
    private final GraphQLType fieldType;
    private final List<Field> fields;
    private final Object result;
    private final Map<String, Object> argumentValues;

    private ValueCompletionParameters(ExecutionContext executionContext, GraphQLType fieldType,
                                      List<Field> fields, Object result, Map<String, Object> argumentValues) {

        this.executionContext = executionContext;
        this.fieldType = fieldType;
        this.fields = fields;
        this.result = result;
        this.argumentValues = argumentValues;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    @SuppressWarnings("unchecked")
    public <T extends GraphQLType> T getFieldType() {
        return (T) fieldType;
    }

    public List<Field> getFields() {
        return fields;
    }

    @SuppressWarnings("unchecked")
    public <T> T getResult() {
        return (T) result;
    }

    public Map<String, Object> getArgumentValues() {
        return argumentValues;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static class Builder {

        private ExecutionContext executionContext;
        private GraphQLType fieldType;
        private List<Field> fields;
        private Object result;
        private Map<String, Object> argumentValues;

        public Builder executionContext(ExecutionContext executionContext) {
            this.executionContext = executionContext;
            return this;
        }

        public Builder fieldType(GraphQLType fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        public Builder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        public Builder argumentValues(Map<String, Object> argumentValues) {
            this.argumentValues = argumentValues;
            return this;
        }

        public ValueCompletionParameters build() {
            return new ValueCompletionParameters(executionContext, fieldType, fields, result, argumentValues);
        }
    }
}
