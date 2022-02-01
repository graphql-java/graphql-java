package graphql.execution.nextgen.result;

import graphql.Internal;
import graphql.execution.ExecutionStepInfo;

import java.util.Collections;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public class UnresolvedObjectResultNode extends ObjectExecutionResultNode {

    public UnresolvedObjectResultNode(ExecutionStepInfo executionStepInfo, ResolvedValue resolvedValue) {
        super(executionStepInfo, resolvedValue, Collections.emptyList(), Collections.emptyList());
    }

}