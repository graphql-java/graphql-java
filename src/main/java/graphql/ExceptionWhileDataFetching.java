package graphql;


import graphql.language.SourceLocation;

import java.util.List;

/**
 * <p>ExceptionWhileDataFetching class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class ExceptionWhileDataFetching implements GraphQLError {

    private final Exception exception;

    /**
     * <p>Constructor for ExceptionWhileDataFetching.</p>
     *
     * @param exception a {@link java.lang.Exception} object.
     */
    public ExceptionWhileDataFetching(Exception exception) {
        this.exception = exception;
    }

    /**
     * <p>Getter for the field <code>exception</code>.</p>
     *
     * @return a {@link java.lang.Exception} object.
     */
    public Exception getException() {
        return exception;
    }


    /** {@inheritDoc} */
    @Override
    public String getMessage() {
        return "Exception while fetching data: " + exception.toString();
    }

    /** {@inheritDoc} */
    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public ErrorType getErrorType() {
        return ErrorType.DataFetchingException;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ExceptionWhileDataFetching{" +
                "exception=" + exception +
                '}';
    }
}
