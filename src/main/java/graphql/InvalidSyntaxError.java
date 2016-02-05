package graphql;


import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

public class InvalidSyntaxError implements GraphQLError {

    private final List<SourceLocation> sourceLocations = new ArrayList<SourceLocation>();

    public InvalidSyntaxError(SourceLocation sourceLocation) {
        if (sourceLocation != null)
            this.sourceLocations.add(sourceLocation);
    }

    public InvalidSyntaxError(List<SourceLocation> sourceLocations) {
        if (sourceLocations != null) {
            this.sourceLocations.addAll(sourceLocations);
        }
    }


    @Override
    public String getMessage() {
        return "Invalid Syntax";
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

}
