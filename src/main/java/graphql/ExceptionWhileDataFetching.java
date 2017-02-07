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
        return exception.getMessage();
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExceptionWhileDataFetching that = (ExceptionWhileDataFetching) o;

        return Helper.equals(this, that);
    }

    @Override
    public int hashCode() {
        return Helper.hashCode(this);
    }
}
