package graphql.execution.nextgen.result;

import graphql.Internal;
import graphql.execution.ExecutionStepInfo;

import java.util.Collections;

@Internal
public class UnresolvedObjectResultNode extends ObjectExecutionResultNode {

    public UnresolvedObjectResultNode(ExecutionStepInfo executionStepInfo, ResolvedValue resolvedValue) {
        super(executionStepInfo, resolvedValue, Collections.emptyList(), Collections.emptyList());
    }

}