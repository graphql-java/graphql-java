package graphql;


import graphql.execution.ExecutionPath;
import graphql.language.SourceLocation;
import graphql.schema.CoercingSerializeException;

import java.util.List;

import static java.lang.String.format;

@PublicApi
public class SerializationError implements GraphQLError {

    private final CoercingSerializeException exception;
    private final ExecutionPath path;

    public SerializationError(ExecutionPath path, CoercingSerializeException exception) {
        this.path = path;
        this.exception = exception;
    }

    public CoercingSerializeException getException() {
        return exception;
    }


    @Override
    public String getMessage() {
        return format("Can't serialize value %s: %s", path == null ? "" : path, exception.getMessage());
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.DataFetchingException;
    }

    /**
     * The graphql spec says that that path field of any error should be a list
     * of path entries - http://facebook.github.io/graphql/#sec-Errors
     *
     * @return the path in list format
     */
    public List<Object> getPath() {
        return path == null ? null : path.toList();
    }

    @Override
    public String toString() {
        return "ExceptionWhileDataFetching{" +
                "path=" + path +
                "exception=" + exception +
                '}';
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return Helper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Helper.hashCode(this);
    }
}
