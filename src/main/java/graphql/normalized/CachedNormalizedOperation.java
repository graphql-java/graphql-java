package graphql.normalized;

import graphql.Internal;
import graphql.language.OperationDefinition;

import java.util.List;

/**
 * A cached, variable-independent representation of a normalized GraphQL operation.
 * <p>
 * This is the cacheable intermediate between the raw AST and the executable
 * {@link ExecutableNormalizedOperation}. It holds a tree of {@link CachedNormalizedField}s
 * that preserve the full query structure with unevaluated skip/include conditions and
 * uncoerced arguments.
 * <p>
 * Unlike {@link ExecutableNormalizedOperation}, fields are NOT merged across type conditions
 * if they have different inclusion conditions. This allows correct materialization for
 * any set of variable values.
 *
 * @see CachedNormalizedOperationFactory
 * @see CachedOperationMaterializer
 */
@Internal
public class CachedNormalizedOperation {

    private final OperationDefinition.Operation operation;
    private final String operationName;
    private final List<CachedNormalizedField> topLevelFields;
    private final int fieldCount;
    private final int maxDepth;

    public CachedNormalizedOperation(
            OperationDefinition.Operation operation,
            String operationName,
            List<CachedNormalizedField> topLevelFields,
            int fieldCount,
            int maxDepth
    ) {
        this.operation = operation;
        this.operationName = operationName;
        this.topLevelFields = topLevelFields;
        this.fieldCount = fieldCount;
        this.maxDepth = maxDepth;
    }

    public OperationDefinition.Operation getOperation() {
        return operation;
    }

    public String getOperationName() {
        return operationName;
    }

    public List<CachedNormalizedField> getTopLevelFields() {
        return topLevelFields;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    public int getMaxDepth() {
        return maxDepth;
    }
}
