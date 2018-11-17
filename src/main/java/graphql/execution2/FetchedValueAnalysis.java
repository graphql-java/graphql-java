package graphql.execution2;

import graphql.GraphQLError;
import graphql.execution.ExecutionStepInfo;

import java.util.ArrayList;
import java.util.List;

public class FetchedValueAnalysis {


    public enum FetchedValueType {
        OBJECT,
        LIST,
        SCALAR,
        ENUM,
        DEFER
    }


    private FetchedValueType valueType;
    private List<GraphQLError> errors;

    // not applicable for LIST
    private Object completedValue;

    private boolean nullValue;

    // only available for LIST
    private List<FetchedValueAnalysis> children;

    // only for object
    private FieldSubSelection fieldSubSelection;


    // for children of list name is the same as for the list itself
    private String name;

    private ExecutionStepInfo executionStepInfo;
    private FetchedValue fetchedValue;


    private FetchedValueAnalysis(Builder builder) {
        setValueType(builder.valueType);
        setErrors(builder.errors);
        setCompletedValue(builder.completedValue);
        setFetchedValue(builder.fetchedValue);
        setChildren(builder.children);
        setNullValue(builder.nullValue);
        setName(builder.name);
        setFieldSubSelection(builder.fieldSubSelection);
        setExecutionStepInfo(builder.executionInfo);
    }


    public static Builder newBuilder(FetchedValueAnalysis copy) {
        Builder builder = new Builder();
        builder.valueType = copy.getValueType();
        builder.errors = copy.getErrors();
        builder.completedValue = copy.getCompletedValue();
        builder.fetchedValue = copy.getFetchedValue();
        builder.children = copy.getChildren();
        builder.nullValue = copy.isNullValue();
        builder.name = copy.getName();
        builder.fieldSubSelection = copy.fieldSubSelection;
        builder.executionInfo = copy.getExecutionStepInfo();
        return builder;
    }

    public FetchedValueType getValueType() {
        return valueType;
    }

    public void setValueType(FetchedValueType valueType) {
        this.valueType = valueType;
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    public void setErrors(List<GraphQLError> errors) {
        this.errors = errors;
    }

    public Object getCompletedValue() {
        return completedValue;
    }

    public void setCompletedValue(Object completedValue) {
        this.completedValue = completedValue;
    }

    public List<FetchedValueAnalysis> getChildren() {
        return children;
    }

    public void setChildren(List<FetchedValueAnalysis> children) {
        this.children = children;
    }

    public boolean isNullValue() {
        return nullValue;
    }

    public void setNullValue(boolean nullValue) {
        this.nullValue = nullValue;
    }


    public FetchedValue getFetchedValue() {
        return fetchedValue;
    }

    public void setFetchedValue(FetchedValue fetchedValue) {
        this.fetchedValue = fetchedValue;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static Builder newFetchedValueAnalysis() {
        return new Builder();
    }

    public static Builder newFetchedValueAnalysis(FetchedValueType valueType) {
        return new Builder().valueType(valueType);
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }

    public void setExecutionStepInfo(ExecutionStepInfo executionInfo) {
        this.executionStepInfo = executionInfo;
    }

    public FieldSubSelection getFieldSubSelection() {
        return fieldSubSelection;
    }

    public void setFieldSubSelection(FieldSubSelection fieldSubSelection) {
        this.fieldSubSelection = fieldSubSelection;
    }

    @Override
    public String toString() {
        return "FetchedValueAnalysis{" +
                "valueType=" + valueType +
                ", errors=" + errors +
                ", completedValue=" + completedValue +
                ", children=" + children +
                ", nullValue=" + nullValue +
                ", name='" + name + '\'' +
                ", fieldSubSelection=" + fieldSubSelection +
                ", executionStepInfo=" + executionStepInfo +
                ", fetchedValue=" + fetchedValue +
                '}';
    }

    public static final class Builder {
        private FetchedValueType valueType;
        private List<GraphQLError> errors = new ArrayList<>();
        private Object completedValue;
        private FetchedValue fetchedValue;
        private List<FetchedValueAnalysis> children;
        private FieldSubSelection fieldSubSelection;
        private boolean nullValue;
        private String name;
        private ExecutionStepInfo executionInfo;

        private Builder() {
        }


        public Builder valueType(FetchedValueType val) {
            valueType = val;
            return this;
        }

        public Builder errors(List<GraphQLError> val) {
            errors = val;
            return this;
        }

        public Builder error(GraphQLError val) {
            errors.add(val);
            return this;
        }


        public Builder completedValue(Object val) {
            completedValue = val;
            return this;
        }

        public Builder children(List<FetchedValueAnalysis> val) {
            children = val;
            return this;
        }


        public Builder nullValue() {
            nullValue = true;
            return this;
        }

        public Builder name(String val) {
            name = val;
            return this;
        }

        public Builder fieldSubSelection(FieldSubSelection fieldSubSelection) {
            this.fieldSubSelection = fieldSubSelection;
            return this;
        }

        public Builder executionStepInfo(ExecutionStepInfo executionInfo) {
            this.executionInfo = executionInfo;
            return this;
        }

        public FetchedValueAnalysis build() {
            return new FetchedValueAnalysis(this);
        }
    }
}
