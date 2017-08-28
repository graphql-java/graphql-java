package graphql;


import graphql.language.SourceLocation;
import org.antlr.v4.runtime.RecognitionException;

import java.util.ArrayList;
import java.util.List;

public class InvalidSyntaxError implements GraphQLError {

    private final String message;
    private final List<SourceLocation> locations = new ArrayList<>();

    public InvalidSyntaxError(SourceLocation sourceLocation, String msg) {
        this.message = mkMessage(msg);
        if (sourceLocation != null) {
            this.locations.add(sourceLocation);
        }
    }

    public InvalidSyntaxError(List<SourceLocation> sourceLocations, String msg) {
        this.message = mkMessage(msg);
        if (sourceLocations != null) {
            this.locations.addAll(sourceLocations);
        }
    }

    private String mkMessage(String msg) {
        return "Invalid Syntax" + (msg == null ? "" : " : " + msg);
    }


    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.InvalidSyntax;
    }

    @Override
    public String toString() {
        return "InvalidSyntaxError{" +
                " message=" + message +
                " ,locations=" + locations +
                '}';
    }


    /**
     * Creates an invalid syntax error object from an exception
     *
     * @param parseException the parse exception
     *
     * @return a new invalid syntax error object
     */
    public static InvalidSyntaxError toInvalidSyntaxError(Exception parseException) {
        String msg = parseException.getMessage();
        SourceLocation sourceLocation = null;
        if (parseException.getCause() instanceof RecognitionException) {
            RecognitionException recognitionException = (RecognitionException) parseException.getCause();
            msg = recognitionException.getMessage();
            sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
        }
        return new InvalidSyntaxError(sourceLocation, msg);
    }


    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return GraphqlErrorHelper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return GraphqlErrorHelper.hashCode(this);
    }
}
