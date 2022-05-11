package graphql.execution.preparsed.persisted;

import graphql.ErrorClassification;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class PersistedQueryError extends RuntimeException implements ErrorClassification {

    Map<String, Object> getExtensions() {
        return new LinkedHashMap<>();
    }
}
