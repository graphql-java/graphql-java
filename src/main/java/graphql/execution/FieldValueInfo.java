package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertNotNull;

/**
 * The {@link FieldValueInfo} holds the type of field that was fetched and completed along with the completed value.
 * <p>
 * A field value is considered when its is both fetch via a {@link graphql.schema.DataFetcher} to a raw value, and then
 * it is serialized into scalar or enum or if it's an object type, it is completed as an object given its field sub selection
 * <p>
 * The {@link #getFieldValueObject()} method returns either a materialized value or a {@link CompletableFuture}
 * promise to a materialized value.  Simple in-memory values will tend to be materialized, while complicated
 * values might need a call to a database or other systems will tend to be {@link CompletableFuture} promises.
 */
@PublicApi
@NullMarked
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
        assertNotNull(fieldValueInfos, "fieldValueInfos can't be null");
        this.completeValueType = completeValueType;
        this.fieldValueObject = fieldValueObject;
        this.fieldValueInfos = fieldValueInfos;
    }

    /**
     * This is an enum that represents the type of field value that was completed for a field
     *
     * @return the type of field value
     */
    public CompleteValueType getCompleteValueType() {
        return completeValueType;
    }

    /**
     * This value can be either an object that is materialized or a {@link CompletableFuture} promise to a value
     *
     * @return either an object that is materialized or a {@link CompletableFuture} promise to a value
     */
    public Object /* CompletableFuture<Object> | Object */ getFieldValueObject() {
        return fieldValueObject;
    }

    /**
     * This returns the value in {@link CompletableFuture} form.  If it is already a {@link CompletableFuture} it is returned
     * directly, otherwise the materialized value is wrapped in a {@link CompletableFuture} and returned
     *
     * @return a {@link CompletableFuture} promise to the value
     */
    public CompletableFuture<Object> getFieldValueFuture() {
        return Async.toCompletableFuture(fieldValueObject);
    }

    /**
     * @return true if the value is a {@link CompletableFuture} promise to a value
     */
    public boolean isFutureValue() {
        return fieldValueObject instanceof CompletableFuture;
    }

    /**
     * When the {@link #getCompleteValueType()} is {@link CompleteValueType#LIST} this holds the list
     * of completed values inside that list object.
     *
     * @return the list of completed field values inside a list
     */
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