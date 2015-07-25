package graphql;


import graphql.language.SourceLocation;

public class InvalidSyntaxError implements GraphQLError {

    public InvalidSyntaxError(SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    private final SourceLocation sourceLocation;

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public ErrorType geErrorType() {
        return ErrorType.InvalidSyntax;
    }

    @Override
    public String toString() {
        return "InvalidSyntaxError{" +
                "sourceLocation=" + sourceLocation +
                '}';
    }

}
