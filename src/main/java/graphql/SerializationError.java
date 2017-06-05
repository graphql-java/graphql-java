package graphql;


import graphql.language.SourceLocation;
import graphql.schema.CoercingSerializeException;

import java.util.List;

@PublicApi
public class SerializationError implements GraphQLError {

    private final CoercingSerializeException exception;

    public SerializationError(CoercingSerializeException exception) {
        this.exception = exception;
    }

    public CoercingSerializeException getException() {
        return exception;
    }


    @Override
    public String getMessage() {
        return "Can't serialize value: " + exception.getMessage();
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.DataFetchingException;
    }

    @Override
    public String toString() {
        return "ExceptionWhileDataFetching{" +
                "exception=" + exception +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        return Helper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Helper.hashCode(this);
    }
}
