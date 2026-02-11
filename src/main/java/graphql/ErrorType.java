package graphql;


import org.jspecify.annotations.NullMarked;

/**
 * All the errors in graphql belong to one of these categories
 */
@PublicApi
@NullMarked
public enum ErrorType implements ErrorClassification {
    InvalidSyntax,
    ValidationError,
    DataFetchingException,
    NullValueInNonNullableField,
    OperationNotSupported,
    ExecutionAborted
}
