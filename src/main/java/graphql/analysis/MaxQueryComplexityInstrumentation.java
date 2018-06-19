package graphql.analysis;

import graphql.PublicApi;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.validation.ValidationError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.whenCompleted;

/**
 * Prevents execution if the query complexity is greater than the specified maxComplexity
 */
@PublicApi
public class MaxQueryComplexityInstrumentation extends SimpleInstrumentation {


    private final int maxComplexity;
    private final FieldComplexityCalculator fieldComplexityCalculator;

    /**
     * new Instrumentation with default complexity calculator which is `1 + childComplexity`
     *
     * @param maxComplexity max allowed complexity, otherwise execution will be aborted
     */
    public MaxQueryComplexityInstrumentation(int maxComplexity) {
        this(maxComplexity, (env, childComplexity) -> 1 + childComplexity);
    }

    /**
     * new Instrumentation with custom complexity calculator
     *
     * @param maxComplexity             max allowed complexity, otherwise execution will be aborted
     * @param fieldComplexityCalculator custom complexity calculator
     */
    public MaxQueryComplexityInstrumentation(int maxComplexity, FieldComplexityCalculator fieldComplexityCalculator) {
        this.maxComplexity = maxComplexity;
        this.fieldComplexityCalculator = assertNotNull(fieldComplexityCalculator, "calculator can't be null");
    }


    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return whenCompleted((errors, throwable) -> {
            if ((errors != null && errors.size() > 0) || throwable != null) {
                return;
            }
            QueryTraversal queryTraversal = newQueryTraversal(parameters);

            Map<QueryVisitorFieldEnvironment, List<Integer>> valuesByParent = new LinkedHashMap<>();
            queryTraversal.visitPostOrder(new QueryVisitorStub() {
                @Override
                public void visitField(QueryVisitorFieldEnvironment env) {
                    int childsComplexity = 0;
                    QueryVisitorFieldEnvironment thisNodeAsParent = new QueryVisitorFieldEnvironmentImpl(env.getField(), env.getFieldDefinition(), env.getParentType(), env.getParentEnvironment(), env.getArguments(), env.getSelectionSetContainer());
                    if (valuesByParent.containsKey(thisNodeAsParent)) {
                        childsComplexity = valuesByParent.get(thisNodeAsParent).stream().mapToInt(Integer::intValue).sum();
                    }
                    int value = calculateComplexity(env, childsComplexity);
                    valuesByParent.putIfAbsent(env.getParentEnvironment(), new ArrayList<>());
                    valuesByParent.get(env.getParentEnvironment()).add(value);
                }
            });
            int totalComplexity = valuesByParent.get(null).stream().mapToInt(Integer::intValue).sum();
            if (totalComplexity > maxComplexity) {
                throw mkAbortException(totalComplexity, maxComplexity);
            }
        });
    }

    /**
     * Called to generate your own error message or custom exception class
     *
     * @param totalComplexity the complexity of the query
     * @param maxComplexity   the maximum complexity allowed
     *
     * @return a instance of AbortExecutionException
     */
    protected AbortExecutionException mkAbortException(int totalComplexity, int maxComplexity) {
        return new AbortExecutionException("maximum query complexity exceeded " + totalComplexity + " > " + maxComplexity);
    }

    QueryTraversal newQueryTraversal(InstrumentationValidationParameters parameters) {
        return QueryTraversal.newQueryTraversal()
                .schema(parameters.getSchema())
                .document(parameters.getDocument())
                .operationName(parameters.getOperation())
                .variables(parameters.getVariables())
                .build();
    }

    private int calculateComplexity(QueryVisitorFieldEnvironment QueryVisitorFieldEnvironment, int childsComplexity) {
        FieldComplexityEnvironment fieldComplexityEnvironment = convertEnv(QueryVisitorFieldEnvironment);
        return fieldComplexityCalculator.calculate(fieldComplexityEnvironment, childsComplexity);
    }

    private FieldComplexityEnvironment convertEnv(QueryVisitorFieldEnvironment QueryVisitorFieldEnvironment) {
        FieldComplexityEnvironment parentEnv = null;
        if (QueryVisitorFieldEnvironment.getParentEnvironment() != null) {
            parentEnv = convertEnv(QueryVisitorFieldEnvironment.getParentEnvironment());
        }
        return new FieldComplexityEnvironment(
                QueryVisitorFieldEnvironment.getField(),
                QueryVisitorFieldEnvironment.getFieldDefinition(),
                QueryVisitorFieldEnvironment.getParentType(),
                QueryVisitorFieldEnvironment.getArguments(),
                parentEnv
        );
    }


}
