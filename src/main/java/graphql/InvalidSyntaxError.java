package graphql;


import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

public class InvalidSyntaxError extends GraphQLException implements GraphQLError {

    private final String message;
    private final String sourcePreview;
    private final List<SourceLocation> locations = new ArrayList<>();

    public InvalidSyntaxError(SourceLocation sourceLocation, String msg) {
        this(singletonList(sourceLocation), msg);
    }

    public InvalidSyntaxError(List<SourceLocation> sourceLocations, String msg) {
        this(sourceLocations, msg, null, null);
    }

    public InvalidSyntaxError(List<SourceLocation> sourceLocations, String msg, String sourcePreview, Exception cause) {
        super(cause);
        this.message = mkMessage(msg);
        this.sourcePreview = sourcePreview;
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

    public String getSourcePreview() {
        return sourcePreview;
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
