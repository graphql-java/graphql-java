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
    private final Object /* CompletableFuture<Object> | Object */ fieldValueObject;
    private final List<FieldValueInfo> fieldValueInfos;

    public FieldValueInfo(CompleteValueType completeValueType, Object fieldValueObject) {
        this(completeValueType, fieldValueObject, ImmutableList.of());
    }

    public FieldValueInfo(CompleteValueType completeValueType, Object fieldValueObject, List<FieldValueInfo> fieldValueInfos) {
        assertNotNull(fieldValueInfos, () -> "fieldValueInfos can't be null");
        this.completeValueType = completeValueType;
        this.fieldValueObject = fieldValueObject;
        this.fieldValueInfos = fieldValueInfos;
    }

    public CompleteValueType getCompleteValueType() {
        return completeValueType;
    }

    @Deprecated(since = "2023-09-11")
    public CompletableFuture<ExecutionResult> getFieldValue() {
        return getFieldValueFuture().thenApply(fv -> ExecutionResultImpl.newExecutionResult().data(fv).build());
    }

    public CompletableFuture<Object> getFieldValueFuture() {
        return Async.toCompletableFuture(fieldValueObject);
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
                ", fieldValueObject=" + fieldValueObject +
                ", fieldValueInfos=" + fieldValueInfos +
                '}';
    }

}