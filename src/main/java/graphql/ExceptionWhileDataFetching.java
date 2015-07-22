package graphql;


public class ExceptionWhileDataFetching implements GraphQLError{

    private final Exception exception;

    public ExceptionWhileDataFetching(Exception exception) {
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    @Override
    public String toString() {
        return "ExceptionWhileDataFetching{" +
                "exception=" + exception +
                '}';
    }
}
