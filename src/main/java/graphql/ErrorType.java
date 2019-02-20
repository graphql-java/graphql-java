package graphql;


/**
 * All the errors in graphql belong to one of these categories
 */
@PublicApi
public enum ErrorType implements ErrorTypeClassification {
    InvalidSyntax,
    ValidationError,
    DataFetchingException,
    OperationNotSupported,
    ExecutionAborted
}
