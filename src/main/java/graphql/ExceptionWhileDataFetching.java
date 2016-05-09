package graphql;


import graphql.language.SourceLocation;

import java.util.List;

public class ExceptionWhileDataFetching implements GraphQLError {

    private final Throwable exception;

    public ExceptionWhileDataFetching(Throwable exception) {
        this.exception = exception;
    }

    public Throwable getException() {
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
    public ErrorType getErrorType() {
        return ErrorType.DataFetchingException;
    }

    @Override
    public String toString() {
        return "ExceptionWhileDataFetching{" +
                "exception=" + exception +
                '}';
    }
}
