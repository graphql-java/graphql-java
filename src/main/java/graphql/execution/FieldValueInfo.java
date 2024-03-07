package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
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
    private final List<FieldValueInfo> fieldValueInfos;

    FieldValueInfo(CompleteValueType completeValueType, CompletableFuture<Object> fieldValue) {
        this(completeValueType, fieldValue, ImmutableList.of());
    }

    FieldValueInfo(CompleteValueType completeValueType, CompletableFuture<Object> fieldValue, List<FieldValueInfo> fieldValueInfos) {
        Assert.assertNotNull(fieldValueInfos, () -> "fieldValueInfos can't be null");
        this.completeValueType = completeValueType;
        this.fieldValue = fieldValue;
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