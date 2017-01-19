package graphql;


import graphql.execution.Path;
import graphql.language.SourceLocation;

import java.util.List;

public class ExceptionWhileDataFetching implements GraphQLError {

    private final Path path;
    private final Throwable exception;

    public ExceptionWhileDataFetching(Throwable exception) {
        this(null, exception);
    }

    public ExceptionWhileDataFetching(Path path, Throwable exception) {
        this.path = path;
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

    public String getPath() {
        return path == null ? null : path.toString();
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
