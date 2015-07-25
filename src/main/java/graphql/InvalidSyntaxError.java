package graphql;


public class InvalidSyntaxError implements GraphQLError {
    @Override
    public ErrorType geErrorType() {
        return ErrorType.InvalidSyntax;
    }
}
