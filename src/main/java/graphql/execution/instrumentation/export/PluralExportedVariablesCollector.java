package graphql.execution.instrumentation.export;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluralExportedVariablesReducer implements ExportedVariablesReducer{

    @Override
    public Map<String, Object> reduceCollectedVariables(Map<String, List<Object>> collectedVariables) {
        Map<String, Object> outputMap = new HashMap<>();
        for (String key : collectedVariables.keySet()) {
            List<Object> objectList = collectedVariables.get(key);
            if (isPlural(key)) {
                outputMap.put(key,reduceList(objectList));
            }
        }
    }

    private boolean isPlural(String key) {
        return false;
    }
}
