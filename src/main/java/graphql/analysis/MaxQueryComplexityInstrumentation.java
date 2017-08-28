package graphql.analysis;

import graphql.PublicApi;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.NodeUtil;
import graphql.validation.ValidationError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PublicApi
public class MaxQueryComplexityInstrumentation extends NoOpInstrumentation {


    private int maxComplexity;

    public MaxQueryComplexityInstrumentation(int maxComplexity) {
        this.maxComplexity = maxComplexity;
    }


    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return (result, throwable) -> {
            NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(parameters.getDocument(), parameters.getOperation());
            QueryTraversal queryTraversal = new QueryTraversal(
                    getOperationResult.operationDefinition,
                    parameters.getSchema(),
                    getOperationResult.fragmentsByName,
                    parameters.getVariables()
            );

            Map<QueryPath, List<Integer>> valuesByParent = new LinkedHashMap<>();
            queryTraversal.visitPostOrder(env -> {
                int childsComplexity = 0;
                QueryPath thisNodeAsParent = new QueryPath(env.getField(), env.getFieldDefinition(), env.getParentType(), env.getPath());
                if (valuesByParent.containsKey(thisNodeAsParent)) {
                    childsComplexity = valuesByParent.get(thisNodeAsParent).stream().mapToInt(Integer::intValue).sum();
                }
                int value = calculateComplexity(env, childsComplexity);
                valuesByParent.putIfAbsent(env.getPath(), new ArrayList<>());
                valuesByParent.get(env.getPath()).add(value);
            });
            int totalComplexity = valuesByParent.get(null).stream().mapToInt(Integer::intValue).sum();
            if (totalComplexity > maxComplexity) {
                throw new AbortExecutionException("maximum query complexity exceeded " + totalComplexity + " > " + maxComplexity);
            }
        };
    }

    private Integer calculateComplexity(QueryVisitorEnvironment environment, int childCount) {
        // interface call here ...
        return 1 + childCount;
    }

}
