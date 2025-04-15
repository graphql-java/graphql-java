package graphql.execution;

import com.google.common.collect.Maps;
import graphql.Internal;

import java.util.List;
import java.util.Map;

/**
 * Implements the contract of {@link ResponseMapFactory} with {@link java.util.LinkedHashMap}.
 * This is the default of graphql-java since a long time and changing it could cause breaking changes.
 */
@Internal
public class DefaultResponseMapFactory implements ResponseMapFactory {

    @Override
    public Map<String, Object> createInsertionOrdered(List<String> keys, List<Object> values) {
        Map<String, Object> result = Maps.newLinkedHashMapWithExpectedSize(keys.size());
        int ix = 0;
        for (Object fieldValue : values) {
            String fieldName = keys.get(ix++);
            result.put(fieldName, fieldValue);
        }
        return result;
    }
}
