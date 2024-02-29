package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertNotNull;

@PublicApi
public class FieldValueInfo {

    public enum CompleteValueType {
        OBJECT,
        LIST,
        NULL,
        SCALAR,
        ENUM

    }

    private final CompleteValueType completeValueType;
    private final CompletableFuture<Object> fieldValue;
    private final Object /* CompletableFuture<Object> | Object */ fieldValueObject;
    private final List<FieldValueInfo> fieldValueInfos;

    private FieldValueInfo(CompleteValueType completeValueType, Object fieldValueObject, List<FieldValueInfo> fieldValueInfos) {
        assertNotNull(fieldValueInfos, () -> "fieldValueInfos can't be null");
        this.completeValueType = completeValueType;
        this.fieldValueObject = fieldValueObject;
        this.fieldValue = Async.toCompletableFuture(fieldValueObject);
        this.fieldValueInfos = fieldValueInfos;
    }

    public CompleteValueType getCompleteValueType() {
        return completeValueType;
    }

    @Deprecated(since="2023-09-11" )
    public CompletableFuture<ExecutionResult> getFieldValue() {
        return fieldValue.thenApply(fv -> ExecutionResultImpl.newExecutionResult().data(fv).build());
    }
    public CompletableFuture<Object> getFieldValueFuture() {
        return fieldValue;
    }
    public Object /* CompletableFuture<Object> | Object */ getFieldValueObject() {
        return fieldValueObject;
    }

    public boolean isFutureValue() {
        return fieldValueObject instanceof CompletableFuture;
    }
    public List<FieldValueInfo> getFieldValueInfos() {
        return fieldValueInfos;
    }

    public static Builder newFieldValueInfo(CompleteValueType completeValueType) {
        return new Builder(completeValueType);
    }

    @Override
    public String toString() {
        return "FieldValueInfo{" +
                "completeValueType=" + completeValueType +
                ", fieldValue=" + fieldValue +
                ", fieldValueInfos=" + fieldValueInfos +
                '}';
    }

    @SuppressWarnings("unused")
    public static class Builder {
        private CompleteValueType completeValueType;
        private Object fieldValueObject;
        private List<FieldValueInfo> listInfos = new ArrayList<>();

        public Builder(CompleteValueType completeValueType) {
            this.completeValueType = completeValueType;
        }

        public Builder completeValueType(CompleteValueType completeValueType) {
            this.completeValueType = completeValueType;
            return this;
        }

// KILL for now in the PR - probably want to kill this anyway for reals
//
//        public Builder fieldValue(CompletableFuture<Object> executionResultFuture) {
//            this.fieldValueObject = executionResultFuture;
//            return this;
//        }

        public Builder fieldValueObject(Object fieldValueObject) {
            this.fieldValueObject = fieldValueObject;
            return this;
        }

        public Builder fieldValueInfos(List<FieldValueInfo> listInfos) {
            assertNotNull(listInfos, () -> "fieldValueInfos can't be null");
            this.listInfos = listInfos;
            return this;
        }

        public FieldValueInfo build() {
            return new FieldValueInfo(completeValueType, fieldValueObject, listInfos);
        }
    }
}