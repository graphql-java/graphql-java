package graphql.execution.nextgen;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.ExecutionStepInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

@Internal
public class FetchedValueAnalysis {


    public enum FetchedValueType {
        OBJECT,
        LIST,
        SCALAR,
        ENUM,
        DEFER
    }


    private final FetchedValueType valueType;
    private final List<GraphQLError> errors;

    // not applicable for LIST
    private final Object completedValue;

    private final boolean nullValue;

    // only available for LIST
    private final List<FetchedValueAnalysis> children;

    // only for object
    private final FieldSubSelection fieldSubSelection;


    // for children of list name is the same as for the list itself
    private final String name;

    private final ExecutionStepInfo executionStepInfo;
    // for LIST this is the whole list
    private final FetchedValue fetchedValue;


    private FetchedValueAnalysis(Builder builder) {
        this.valueType = assertNotNull(builder.valueType);
        this.errors = new ArrayList<>(builder.errors);
        this.completedValue = builder.completedValue;
        this.nullValue = builder.nullValue;
        this.children = builder.children;
        this.fieldSubSelection = builder.fieldSubSelection;
        this.name = builder.name;
        this.executionStepInfo = assertNotNull(builder.executionInfo);
        this.fetchedValue = assertNotNull(builder.fetchedValue);
    }


    public FetchedValueType getValueType() {
        return valueType;
    }


    public List<GraphQLError> getErrors() {
        return errors;
    }

    public Object getCompletedValue() {
        return completedValue;
    }

    public List<FetchedValueAnalysis> getChildren() {
        return children;
    }

    public boolean isNullValue() {
        return nullValue;
    }


    public FetchedValue getFetchedValue() {
        return fetchedValue;
    }

    public String getName() {
        return name;
    }

    public FetchedValueAnalysis transfrom(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }


    public static Builder newFetchedValueAnalysis() {
        return new Builder();
    }

    public static Builder newFetchedValueAnalysis(FetchedValueType valueType) {
        return new Builder().valueType(valueType);
    }

    public static Builder newFetchedValueAnalysis(FetchedValueAnalysis existing) {
        return new Builder(existing);
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }


    public FieldSubSelection getFieldSubSelection() {
        return fieldSubSelection;
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

        private Builder(FetchedValueAnalysis existing) {
            valueType = existing.getValueType();
            errors = existing.getErrors();
            completedValue = existing.getCompletedValue();
            fetchedValue = existing.getFetchedValue();
            children = existing.getChildren();
            fieldSubSelection = existing.fieldSubSelection;
            nullValue = existing.isNullValue();
            name = existing.getName();
            executionInfo = existing.getExecutionStepInfo();
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

        public Builder fetchedValue(FetchedValue fetchedValue) {
            this.fetchedValue = fetchedValue;
            return this;
        }

        public FetchedValueAnalysis build() {
            return new FetchedValueAnalysis(this);
        }
    }
}
