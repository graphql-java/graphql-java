package graphql;


import graphql.language.SourceLocation;

import java.util.List;

@PublicApi
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
        return "Exception while fetching data: " + exception.getMessage();
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
