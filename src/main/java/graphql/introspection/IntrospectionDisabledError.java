package graphql.introspection;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;

@Internal
public class IntrospectionDisabledError implements GraphQLError {

    private final List<SourceLocation> locations;

    public IntrospectionDisabledError(SourceLocation sourceLocation) {
        locations = sourceLocation == null ? Collections.emptyList() : Collections.singletonList(sourceLocation);
    }

    @Override
    public String getMessage() {
        return "Introspection has been disabled for this request";
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    public ErrorClassification getErrorType() {
        return ErrorClassification.errorClassification("IntrospectionDisabled");
    }
}
