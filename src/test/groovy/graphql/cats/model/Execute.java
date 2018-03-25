package graphql.cats.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Execute {

    private Object testValue;
    private String operationName;
    private Map variables;
    private Boolean validateQuery = true;

    static Execute fromYaml(Map yaml) {
        Execute execute = new Execute();
        if (yaml == null) {
            return execute;
        }
        execute.testValue = yaml.get("test-value");
        execute.operationName = (String) yaml.get("operation-name");
        execute.variables = strMap((Map) yaml.get("variables"));
        execute.validateQuery = yaml.containsKey("validate-query");
        return execute;
    }

    private static Map<String, Object> strMap(Map variables) {
        if (variables == null) {
            return null;
        }
        Map<String, Object> stringObjectMap = new LinkedHashMap<>();
        for (Object key : variables.keySet()) {
            stringObjectMap.put(String.valueOf(key), variables.get(key));
        }
        return stringObjectMap;
    }

    public Optional<Object> getTestValue() {
        return Optional.ofNullable(testValue);
    }

    public Optional<String> getOperationName() {
        return Optional.ofNullable(operationName);
    }

    public Map<String, Object> getVariables() {
        return variables == null ? Collections.emptyMap() : variables;
    }

    public boolean getValidateQuery() {
        return validateQuery;
    }


}
