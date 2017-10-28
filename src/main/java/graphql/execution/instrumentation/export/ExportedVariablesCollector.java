package graphql.execution.instrumentation.export;

import java.util.List;
import java.util.Map;

public interface ExportedVariablesReducer {

    Map<String, Object> reduceCollectedVariables(Map<String, List<Object>> collectedVariables);
}
