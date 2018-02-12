package graphql.execution.instrumentation.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This exported variables collector will collapse all values into a list if the exported name
 * ends with s - eg its a plural word.
 *
 * So values for an exported variable called "hostnames" will be a list of values where
 * as if the exported variable is called "hostname" then it will be a singleton value, that is
 * the last one encountered.
 */
public class PluralExportedVariablesCollector implements ExportedVariablesCollector {

    private final ConcurrentHashMap<String, Object> variables = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    @Override
    public void collect(ExportedVariablesCollectionEnvironment env) {
        String variableName = env.getVariableName();
        Object variableValue = env.getVariableValue();
        Object putValue;
        if (isPlural(variableName)) {
            putValue = asList(variableName, variableValue);
        } else {
            putValue = variableValue;
        }
        variables.put(variableName, putValue);
    }

    @SuppressWarnings("unchecked")
    private Object asList(String variableName, Object variableValue) {
        Object putValue;List<Object> currentValue = (List<Object>) variables.getOrDefault(variableName, new ArrayList<>());
        if (variableValue instanceof Collection) {
            currentValue.addAll(((Collection) variableValue));
        } else {
            currentValue.add(variableValue);
        }
        putValue = currentValue;
        return putValue;
    }

    private boolean isPlural(String variableName) {
        return variableName.toLowerCase().endsWith("s");
    }

    @Override
    public String toString() {
        return "PluralExportedVariablesCollector{" +
                "variables=" + variables +
                '}';
    }
}
