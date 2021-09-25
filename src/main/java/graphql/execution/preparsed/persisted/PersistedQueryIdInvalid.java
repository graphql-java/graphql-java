package graphql.execution.preparsed.persisted;

import graphql.PublicApi;

import java.util.LinkedHashMap;
import java.util.Map;

@PublicApi
public class PersistedQueryIdInvalid extends PersistedQueryError {
    private final Object persistedQueryId;

    public PersistedQueryIdInvalid(Object persistedQueryId) {
        this.persistedQueryId = persistedQueryId;
    }

    @Override
    public String getMessage() {
        return "PersistedQueryIdInvalid";
    }

    public Object getPersistedQueryId() {
        return persistedQueryId;
    }

    @Override
    public String toString() {
        return "PersistedQueryIdInvalid";
    }

    public Map<String, Object> getExtensions() {
        LinkedHashMap<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("persistedQueryId", getPersistedQueryId());
        return extensions;
    }
}
