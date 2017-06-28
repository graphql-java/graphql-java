package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.language.Field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The standard graphql execution strategy that runs fields in serial order
 */
public class SimpleExecutionStrategy extends ExecutionStrategy {

    /**
     * The standard graphql execution strategy that runs fields in serial order 
     */
    public SimpleExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates a simple execution handler that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use
     */
    public SimpleExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    public ExecutionResult execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        Map<String, List<Field>> fields = parameters.fields();
        Map<String, Object> results = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            List<Field> fieldList = fields.get(fieldName);

            ExecutionPath fieldPath = parameters.path().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters.transform(bldr -> bldr.path(fieldPath));

            try {
                ExecutionResult resolvedResult = resolveField(executionContext, newParameters, fieldList);

                results.put(fieldName, resolvedResult != null ? resolvedResult.getData() : null);
            } catch (NonNullableFieldWasNullException e) {
                /*
                 * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability),
                 *
                 * If a non nullable child field type actually resolves to a null value and the parent type is nullable
                 * then the parent must in fact become null
                 * so we use exceptions to indicate this special case.  However if the parent is in fact a non nullable type
                 * itself then we need to bubble that upwards again until we get to the root in which case the result
                 * is meant to be null.
                 */

                TypeInfo typeInfo = e.getTypeInfo();
                if (typeInfo.hasParentType() && typeInfo.parentTypeInfo().typeIsNonNull()) {
                    throw e;
                }
                results = null;
                break;
            }
        }
        return new ExecutionResultImpl(results, executionContext.getErrors());
    }
}
