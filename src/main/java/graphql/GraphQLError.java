package graphql;


import graphql.language.SourceLocation;

import java.util.List;

public interface GraphQLError {

    String getMessage();

    List<SourceLocation> getLocations();

    ErrorType getErrorType();

}
