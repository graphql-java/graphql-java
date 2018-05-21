package graphql.execution;

import graphql.ExecutionResult;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FieldValueInfo {

    public enum CompleteValueType {
        OBJECT,
        LIST,
        NULL,
        SCALAR,
        ENUM

    }

    CompleteValueType completeValueType;
    CompletableFuture<ExecutionResult> executionResultFuture;
    List<FieldValueInfo> listInfos;

    public FieldValueInfo(CompleteValueType completeValueType, CompletableFuture<ExecutionResult> executionResultFuture)  {
        this.completeValueType = completeValueType;
        this.executionResultFuture = executionResultFuture;
        this.listInfos = Collections.emptyList();
    }

    public FieldValueInfo(CompleteValueType completeValueType, CompletableFuture<ExecutionResult> executionResultFuture, List<FieldValueInfo> listInfos) {
        this.completeValueType = completeValueType;
        this.executionResultFuture = executionResultFuture;
        this.listInfos = listInfos;
    }

    public CompleteValueType getCompleteValueType() {
        return completeValueType;
    }

    public void setCompleteValueType(CompleteValueType completeValueType) {
        this.completeValueType = completeValueType;
    }

    public CompletableFuture<ExecutionResult> getExecutionResultFuture() {
        return executionResultFuture;
    }

    public void setExecutionResultFuture(CompletableFuture<ExecutionResult> executionResultFuture) {
        this.executionResultFuture = executionResultFuture;
    }

    public List<FieldValueInfo> getListInfos() {
        return listInfos;
    }

    public void setListInfos(List<FieldValueInfo> listInfos) {
        this.listInfos = listInfos;
    }

    @Override
    public String toString() {
        return "FieldValueInfo{" +
                "completeValueType=" + completeValueType +
                ", executionResultFuture=" + executionResultFuture +
                ", listInfos=" + listInfos +
                '}';
    }
}