package graphql.execution2;

import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution2.result.ExecutionResultNode;
import graphql.execution2.result.LeafExecutionResultNode;
import graphql.execution2.result.ListExecutionResultNode;
import graphql.execution2.result.ObjectExecutionResultNode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class ResultNodesCreator {

    public ExecutionResultNode createResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        if (fetchedValueAnalysis.isNullValue() && fetchedValueAnalysis.getExecutionStepInfo().isNonNullType()) {
            NonNullableFieldWasNullException nonNullableFieldWasNullException =
                    new NonNullableFieldWasNullException(fetchedValueAnalysis.getExecutionStepInfo(), fetchedValueAnalysis.getExecutionStepInfo().getPath());
            return new LeafExecutionResultNode(fetchedValueAnalysis, nonNullableFieldWasNullException);
        }
        if (fetchedValueAnalysis.isNullValue()) {
            return new LeafExecutionResultNode(fetchedValueAnalysis, null);
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.OBJECT) {
            return createUnresolvedNode(fetchedValueAnalysis);
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.LIST) {
            return createListResultNode(fetchedValueAnalysis);
        }
        return new LeafExecutionResultNode(fetchedValueAnalysis, null);
    }

    private ExecutionResultNode createUnresolvedNode(FetchedValueAnalysis fetchedValueAnalysis) {
        return new ObjectExecutionResultNode.UnresolvedObjectResultNode(fetchedValueAnalysis);
    }

    private Optional<NonNullableFieldWasNullException> getFirstNonNullableException(Collection<ExecutionResultNode> collection) {
        return collection.stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }

    private ExecutionResultNode createListResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        List<ExecutionResultNode> executionResultNodes = fetchedValueAnalysis
                .getChildren()
                .stream()
                .map(this::createResultNode)
                .collect(toList());
        return new ListExecutionResultNode(fetchedValueAnalysis, executionResultNodes);
    }
}
