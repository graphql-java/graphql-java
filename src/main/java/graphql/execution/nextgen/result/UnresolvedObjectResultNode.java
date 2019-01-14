package graphql.execution.nextgen.result;

import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.Collections;

public class UnresolvedObjectResultNode extends ObjectExecutionResultNode {

    public UnresolvedObjectResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        super(fetchedValueAnalysis, Collections.emptyMap());
    }

    @Override
    public String toString() {
        return "UnresolvedObjectResultNode{" +
                "fetchedValueAnalysis=" + getFetchedValueAnalysis() +
                '}';
    }
}