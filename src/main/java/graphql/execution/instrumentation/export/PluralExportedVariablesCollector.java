package graphql.execution.instrumentation.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PluralExportedVariablesCollector implements ExportedVariablesCollector {

    private final ConcurrentHashMap<String, Object> variables = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void collect(ExportedVariablesCollectionEnvironment env) {
        String variableName = env.getVariableName();
        Object variableValue = env.getVariableValue();
        if (isPlural(variableName)) {
            List<Object> currentValue = (List<Object>) variables.getOrDefault(variableName, new ArrayList<>());
            if (variableValue instanceof Collection) {
                currentValue.addAll(((Collection) variableValue));
            } else {
                currentValue.add(variableValue);
            }
            variables.put(variableName, currentValue);
        } else {
            variables.put(variableName, variableValue);
        }
    }

    private boolean isPlural(String variableName) {
        return variableName.toLowerCase().endsWith("s");
    }
}
