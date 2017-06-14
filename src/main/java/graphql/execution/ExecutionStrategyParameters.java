package graphql.execution;

import graphql.Assert;
import graphql.PublicApi;
import graphql.language.Field;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * The parameters that are passed to execution strategies
 */
@PublicApi
public class ExecutionStrategyParameters {
    private final TypeInfo typeInfo;
    private final Object source;
    private final Map<String, Object> arguments;
    private final Map<String, List<Field>> fields;
    private final NonNullableFieldValidator nonNullableFieldValidator;
    private final ExecutionPath path;

    private ExecutionStrategyParameters(TypeInfo typeInfo, Object source, Map<String, List<Field>> fields, Map<String, Object> arguments, NonNullableFieldValidator nonNullableFieldValidator, ExecutionPath path) {
        this.typeInfo = assertNotNull(typeInfo, "typeInfo is null");
        this.fields = assertNotNull(fields, "fields is null");
        this.source = source;
        this.arguments = arguments;
        this.nonNullableFieldValidator = nonNullableFieldValidator;
        this.path = path;
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

    public NonNullableFieldValidator nonNullFieldValidator() {
        return nonNullableFieldValidator;
    }

    public ExecutionPath path() {
        return path;
    }

    public ExecutionStrategyParameters transform(Consumer<Builder> builderConsumer) {
        Builder builder = newParameters(this);
        builderConsumer.accept(builder);
        return builder.build();
    }


    public static Builder newParameters() {
        return new Builder();
    }

    public static Builder newParameters(ExecutionStrategyParameters oldParameters) {
        return new Builder(oldParameters);
    }

    @Override
    public String toString() {
        return String.format("ExecutionStrategyParameters { path=%s, typeInfo=%s, source=%s, fields=%s }",
                path, typeInfo, source, fields);
    }

    public static class Builder {
        TypeInfo typeInfo;
        Object source;
        Map<String, List<Field>> fields;
        Map<String, Object> arguments;
        NonNullableFieldValidator nonNullableFieldValidator;
        ExecutionPath path = ExecutionPath.rootPath();

        private Builder() {
        }

        private Builder(ExecutionStrategyParameters oldParameters) {
            this.typeInfo = oldParameters.typeInfo;
            this.source = oldParameters.source;
            this.fields = oldParameters.fields;
            this.arguments = oldParameters.arguments;
            this.nonNullableFieldValidator = oldParameters.nonNullableFieldValidator;
        }

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

        public Builder nonNullFieldValidator(NonNullableFieldValidator nonNullableFieldValidator) {
            this.nonNullableFieldValidator = Assert.assertNotNull(nonNullableFieldValidator, "requires a NonNullValidator");
            return this;
        }

        public Builder path(ExecutionPath path) {
            this.path = path;
            return this;
        }

        public ExecutionStrategyParameters build() {
            return new ExecutionStrategyParameters(typeInfo, source, fields, arguments, nonNullableFieldValidator, path);
        }
    }
}
