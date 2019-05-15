package graphql.execution.instrumentation.dataloader;

import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoaderRegistry;

import java.util.List;

public class RequestLevelTrackingApproach extends AbstractTrackingApproach {

    public RequestLevelTrackingApproach(List<ExecutionId> executionIds, DataLoaderRegistry dataLoaderRegistry) {
        super(dataLoaderRegistry);
        RequestStack requestStack = getStack();
        executionIds.forEach(requestStack::addExecution);
    }

    @Override
    public InstrumentationState createState(ExecutionId executionId) {
        return null;
    }

}