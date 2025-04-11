package graphql.execution;

import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class DefaultResponseMapFactory implements ResponseMapFactory {

    @Override
    public Map<String, Object> create(List<String> fieldNames, List<Object> results) {
        Map<String, Object> result = Maps.newLinkedHashMapWithExpectedSize(fieldNames.size());
        int ix = 0;
        for (Object fieldValue : results) {
            String fieldName = fieldNames.get(ix++);
            result.put(fieldName, fieldValue);
        }
        return result;
    }
}
