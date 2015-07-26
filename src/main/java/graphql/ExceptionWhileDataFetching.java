package graphql;


import graphql.language.SourceLocation;

import java.util.List;

public class ExceptionWhileDataFetching implements GraphQLError {

    private final Exception exception;

    public ExceptionWhileDataFetching(Exception exception) {
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }


    @Override
    public String getMessage() {
        return "Exception while fetching data: " + exception.toString();
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType geErrorType() {
        return ErrorType.DataFetchingException;
    }

    @Override
    public String toString() {
        return "ExceptionWhileDataFetching{" +
                "exception=" + exception +
                '}';
    }
}
