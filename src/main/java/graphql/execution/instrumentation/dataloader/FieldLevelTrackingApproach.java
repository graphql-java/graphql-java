package graphql.execution.instrumentation.dataloader;

import graphql.Internal;
import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoaderRegistry;

/**
 * This approach uses field level tracking to achieve its aims of making the data loader more efficient.
 * Can handle new/concurrent executions using the same sets of dataloaders, attempting to batch load calls together.
 */
@Internal
public class FieldLevelTrackingApproach extends AbstractTrackingApproach {

    public FieldLevelTrackingApproach(DataLoaderRegistry dataLoaderRegistry) {
        super(dataLoaderRegistry);
    }

    public InstrumentationState createState(ExecutionId executionId) {
        synchronized (getStack()) {
            if (getStack().contains(executionId)) {
                throw new RuntimeException(String.format("Execution id %s already in active execution", executionId));
            }
            getStack().addExecution(executionId);
            return null;
        }
    }
}
