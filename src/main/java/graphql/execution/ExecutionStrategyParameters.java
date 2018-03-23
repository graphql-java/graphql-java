package graphql.execution;

import graphql.Assert;
import graphql.PublicApi;
import graphql.execution.defer.DeferredErrorSupport;
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
    private final ExecutionTypeInfo typeInfo;
    private final Object source;
    private final Map<String, Object> arguments;
    private final Map<String, List<Field>> fields;
    private final NonNullableFieldValidator nonNullableFieldValidator;
    private final ExecutionPath path;
    private final List<Field> currentField;
    private final int listSize;
    private final int currentListIndex;
    private final ExecutionStrategyParameters parent;
    private final DeferredErrorSupport deferredErrorSupport;

    private ExecutionStrategyParameters(ExecutionTypeInfo typeInfo,
                                        Object source,
                                        Map<String, List<Field>> fields,
                                        Map<String, Object> arguments,
                                        NonNullableFieldValidator nonNullableFieldValidator,
                                        ExecutionPath path,
                                        List<Field> currentField,
                                        int listSize,
                                        int currentListIndex,
                                        ExecutionStrategyParameters parent,
                                        DeferredErrorSupport deferredErrorSupport) {

        this.typeInfo = assertNotNull(typeInfo, "typeInfo is null");
        this.fields = assertNotNull(fields, "fields is null");
        this.source = source;
        this.arguments = arguments;
        this.nonNullableFieldValidator = nonNullableFieldValidator;
        this.path = path;
        this.currentField = currentField;
        this.listSize = listSize;
        this.currentListIndex = currentListIndex;
        this.parent = parent;
        this.deferredErrorSupport = deferredErrorSupport;
    }

    public ExecutionTypeInfo getTypeInfo() {
        return typeInfo;
    }

    public Object getSource() {
        return source;
    }

    public Map<String, List<Field>> getFields() {
        return fields;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public NonNullableFieldValidator getNonNullFieldValidator() {
        return nonNullableFieldValidator;
    }

    public ExecutionPath getPath() {
        return path;
    }

    public int getListSize() {
        return listSize;
    }

    public int getCurrentListIndex() {
        return currentListIndex;
    }

    public ExecutionStrategyParameters getParent() {
        return parent;
    }
    public DeferredErrorSupport deferredErrorSupport() {
        return deferredErrorSupport;
    }

    /**
     * This returns the current field in its query representations.  Global fragments mean that
     * a single named field can have multiple representations and different field subselections
     * hence the use of a list of Field
     *
     * @return the current field in list form  or null if this has not be computed yet
     */
    public List<Field> getField() {
        return currentField;
    }

    public ExecutionStrategyParameters transform(Consumer<Builder> builderConsumer) {
        Builder builder = newParameters(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public String toString() {
        return String.format("ExecutionStrategyParameters { path=%s, typeInfo=%s, source=%s, fields=%s }",
                path, typeInfo, source, fields);
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static Builder newParameters(ExecutionStrategyParameters oldParameters) {
        return new Builder(oldParameters);
    }

    public static class Builder {
        ExecutionTypeInfo typeInfo;
        Object source;
        Map<String, List<Field>> fields;
        Map<String, Object> arguments;
        NonNullableFieldValidator nonNullableFieldValidator;
        ExecutionPath path = ExecutionPath.rootPath();
        List<Field> currentField;
        int listSize;
        int currentListIndex;
        ExecutionStrategyParameters parent;
        DeferredErrorSupport deferredErrorSupport = new DeferredErrorSupport();

        /**
         * @see ExecutionStrategyParameters#newParameters()
         */
        private Builder() {
        }

        /**
         * @see ExecutionStrategyParameters#newParameters(ExecutionStrategyParameters)
         */
        private Builder(ExecutionStrategyParameters oldParameters) {
            this.typeInfo = oldParameters.typeInfo;
            this.source = oldParameters.source;
            this.fields = oldParameters.fields;
            this.arguments = oldParameters.arguments;
            this.nonNullableFieldValidator = oldParameters.nonNullableFieldValidator;
            this.currentField = oldParameters.currentField;
            this.deferredErrorSupport = oldParameters.deferredErrorSupport;
            this.path = oldParameters.path;
            this.parent = oldParameters.parent;
            this.listSize = oldParameters.listSize;
            this.currentListIndex = oldParameters.currentListIndex;
        }

        public Builder typeInfo(ExecutionTypeInfo type) {
            this.typeInfo = type;
            return this;
        }

        public Builder typeInfo(ExecutionTypeInfo.Builder type) {
            this.typeInfo = type.build();
            return this;
        }

        public Builder fields(Map<String, List<Field>> fields) {
            this.fields = fields;
            return this;
        }

        public Builder field(List<Field> currentField) {
            this.currentField = currentField;
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

        public Builder listSize(int listSize) {
            this.listSize = listSize;
            return this;
        }

        public Builder currentListIndex(int currentListIndex) {
            this.currentListIndex = currentListIndex;
            return this;
        }

        public Builder parent(ExecutionStrategyParameters parent) {
            this.parent = parent;
            return this;
        }

        public Builder deferredErrorSupport(DeferredErrorSupport deferredErrorSupport) {
            this.deferredErrorSupport = deferredErrorSupport;
            return this;
        }

        public ExecutionStrategyParameters build() {
            return new ExecutionStrategyParameters(typeInfo, source, fields, arguments, nonNullableFieldValidator, path, currentField, listSize, currentListIndex, parent, deferredErrorSupport);
        }
    }
}
