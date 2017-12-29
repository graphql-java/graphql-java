package graphql

import graphql.language.SourceLocation

class DataFetchingErrorGraphQLError implements GraphQLError {

    List<Object> path
    String message
    List<SourceLocation> locations

    DataFetchingErrorGraphQLError(String message) {
        this.message = message
        this.path = null
        this.locations = null
    }

    DataFetchingErrorGraphQLError(String message, List<Object> path) {
        this.message = message
        this.path = path
        this.locations = [new SourceLocation(2, 10)]
    }

    @Override
    String getMessage() {
        return message
    }

    @Override
    List<SourceLocation> getLocations() {
        return locations
    }

    @Override
    ErrorType getErrorType() {
        return ErrorType.DataFetchingException
    }

    @Override
    List<Object> getPath() {
        return path
    }
}
