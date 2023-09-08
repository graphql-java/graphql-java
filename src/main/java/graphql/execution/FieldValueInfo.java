package graphql.execution;

import graphql.DeprecatedAt;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertNotNull;

@PublicApi
public class FieldValueInfo {

    private final CompleteValueType completeValueType;
    private final /* CompletableFuture<Object> | Object */ Object fieldValue;
    private final List<FieldValueInfo> fieldValueInfos;

    private FieldValueInfo(CompleteValueType completeValueType, /* CompletableFuture<Object> | Object */ Object fieldValue, List<FieldValueInfo> fieldValueInfos) {
        assertNotNull(fieldValueInfos, () -> "fieldValueInfos can't be null");
        this.completeValueType = completeValueType;
        this.fieldValue = fieldValue;
        this.fieldValueInfos = fieldValueInfos;
    }

    public static Builder newFieldValueInfo(CompleteValueType completeValueType) {
        return new Builder(completeValueType);
    }

    public CompleteValueType getCompleteValueType() {
        return completeValueType;
    }

    @Deprecated
    @DeprecatedAt("2023-09-08")
    public CompletableFuture<ExecutionResult> getFieldValue() {
        return Async.asCompletableFuture(fieldValue).thenApply(ExecutionResultImpl::asExecutionResult);
    }

    public CompletableFuture<Object> getFieldValueFuture() {
        return Async.asCompletableFuture(fieldValue);
    }

    public Object getFieldValueMaterialised() {
        return fieldValue;
    }

    public boolean isFutureValue() {
        return fieldValue instanceof CompletableFuture;
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

    public enum CompleteValueType {
        OBJECT,
        LIST,
        NULL,
        SCALAR,
        ENUM

    }

    @SuppressWarnings("unused")
    public static class Builder {
        private CompleteValueType completeValueType;
        private /* CompletableFuture<Object> | Object*/ Object fieldValue;
        private List<FieldValueInfo> listInfos = new ArrayList<>();

        public Builder(CompleteValueType completeValueType) {
            this.completeValueType = completeValueType;
        }

        public Builder completeValueType(CompleteValueType completeValueType) {
            this.completeValueType = completeValueType;
            return this;
        }

        /**
         * This sets in a field value that is either a promised {@code CompletableFuture<Object>} or a materialised {@code Object}
         *
         * @param fieldValue the possible promise to a field value or a materialised field value
         *
         * @return this builder
         */
        public Builder fieldValue(/* CompletableFuture<Object> | Object */ Object fieldValue) {
            this.fieldValue = fieldValue;
            return this;
        }

        public Builder fieldValueInfos(List<FieldValueInfo> listInfos) {
            assertNotNull(listInfos, () -> "fieldValueInfos can't be null");
            this.listInfos = listInfos;
            return this;
        }

        public FieldValueInfo build() {
            return new FieldValueInfo(completeValueType, fieldValue, listInfos);
        }
    }
}