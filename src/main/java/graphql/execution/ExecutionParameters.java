package graphql.execution;

import graphql.language.Field;

import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * The parameters that are passed to execution strategies
 */
public class ExecutionParameters {
    private final TypeInfo typeInfo;
    private final Object source;
    private final Map<String, Object> arguments;
    private final Map<String, List<Field>> fields;

    private ExecutionParameters(TypeInfo typeInfo, Object source, Map<String, List<Field>> fields, Map<String, Object> arguments) {
        this.typeInfo = assertNotNull(typeInfo, "");
        this.fields = assertNotNull(fields, "");
        this.source = source;
        this.arguments = arguments;
    }

    public TypeInfo typeInfo() {
        return typeInfo;
    }

    public Object source() {
        return source;
    }

    public Map<String, List<Field>> fields() {
        return fields;
    }

    public Map<String, Object> arguments() {
        return arguments;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("ExecutionParameters { typeInfo=%s, source=%s, fields=%s }",
                typeInfo, source, fields);
    }

    public static class Builder {
        TypeInfo typeInfo;
        Object source;
        Map<String, List<Field>> fields;
        Map<String, Object> arguments;

        public Builder typeInfo(TypeInfo type) {
            this.typeInfo = type;
            return this;
        }

        public Builder typeInfo(TypeInfo.Builder type) {
            this.typeInfo = type.build();
            return this;
        }

        public Builder fields(Map<String, List<Field>> fields) {
            this.fields = fields;
            return this;
        }

        public Builder source(Object source) {
            this.source = source;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public ExecutionParameters build() {
            return new ExecutionParameters(typeInfo, source, fields, arguments);
        }
    }
}
