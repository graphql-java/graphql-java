package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;

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

    @Deprecated(since = "2023-09-11")
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


    @Override
    public String toString() {
        return "FieldValueInfo{" +
                "completeValueType=" + completeValueType +
                ", fieldValue=" + fieldValue +
                ", fieldValueInfos=" + fieldValueInfos +
                '}';
    }

}