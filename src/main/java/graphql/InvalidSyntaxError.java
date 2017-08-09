package graphql;


import graphql.language.SourceLocation;
import org.antlr.v4.runtime.RecognitionException;

import java.util.ArrayList;
import java.util.List;

public class InvalidSyntaxError implements GraphQLError {

    private final String msg;
    private final List<SourceLocation> sourceLocations = new ArrayList<>();

    public InvalidSyntaxError(SourceLocation sourceLocation, String msg) {
        this.msg = mkMsg(msg);
        if (sourceLocation != null) {
            this.sourceLocations.add(sourceLocation);
        }
    }

    public InvalidSyntaxError(List<SourceLocation> sourceLocations, String msg) {
        this.msg = mkMsg(msg);
        if (sourceLocations != null) {
            this.sourceLocations.addAll(sourceLocations);
        }
    }

    private String mkMsg(String msg) {
        return "Invalid Syntax" + (msg == null ? "" : " : " + msg);
    }


    @Override
    public String getMessage() {
        return msg;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.InvalidSyntax;
    }

    @Override
    public String toString() {
        return "InvalidSyntaxError{" +
                "sourceLocations=" + sourceLocations +
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


    @Override
    public boolean equals(Object o) {
        return Helper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Helper.hashCode(this);
    }
}
