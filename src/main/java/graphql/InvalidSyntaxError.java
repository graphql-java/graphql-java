package graphql;


import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

public class InvalidSyntaxError extends GraphQLException implements GraphQLError {

    private final String message;
    private final String sourcePreview;
    private final String offendingToken;
    private final List<SourceLocation> locations = new ArrayList<>();

    public InvalidSyntaxError(SourceLocation sourceLocation, String msg) {
        this(singletonList(sourceLocation), msg);
    }

    public InvalidSyntaxError(List<SourceLocation> sourceLocations, String msg) {
        this(sourceLocations, msg, null, null, null);
    }

    public InvalidSyntaxError(List<SourceLocation> sourceLocations, String msg, String sourcePreview, String offendingToken, Exception cause) {
        super(cause);
        this.message = mkMessage(msg, offendingToken, sourceLocations);
        this.sourcePreview = sourcePreview;
        this.offendingToken = offendingToken;
        if (sourceLocations != null) {
            this.locations.addAll(sourceLocations);
        }
    }

    private String mkMessage(String msg, String offendingToken, List<SourceLocation> sourceLocations) {
        SourceLocation srcLoc = (sourceLocations == null || sourceLocations.isEmpty()) ? null : sourceLocations.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid Syntax :");
        if (msg != null) {
            sb.append(" ").append(msg);
        }
        if (offendingToken != null) {
            sb.append(String.format(" offending token '%s'", offendingToken));
        }
        if (srcLoc != null) {
            sb.append(String.format(" at line %d column %d", srcLoc.getLine(), srcLoc.getColumn()));
        }
        return sb.toString();
    }


    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    public String getSourcePreview() {
        return sourcePreview;
    }

    public String getOffendingToken() {
        return offendingToken;
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
                " ,sourcePreview=" + sourcePreview +
                '}';
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
