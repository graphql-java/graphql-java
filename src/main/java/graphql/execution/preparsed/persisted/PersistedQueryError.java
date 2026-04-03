package graphql.execution.preparsed.persisted;

import graphql.ErrorClassification;
import graphql.GraphQLException;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class PersistedQueryError extends GraphQLException implements ErrorClassification {

    Map<String, Object> getExtensions() {
        return new LinkedHashMap<>();
    }
}
