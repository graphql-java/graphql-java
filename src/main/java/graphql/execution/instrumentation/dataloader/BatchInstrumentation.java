package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLType;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.jetbrains.annotations.Nullable;

import static graphql.execution.instrumentation.InstrumentationState.ofState;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * This graphql {@link graphql.execution.instrumentation.Instrumentation} will dispatch
 * all the contained {@link DataLoader}s when each level of the graphql
 * query is executed.
 * <p>
 * This allows you to use {@link DataLoader}s in your {@link DataFetcher}s
 * to optimal loading of data.
 * <p>
 * A DataLoaderDispatcherInstrumentation will be automatically added to the {@link graphql.GraphQL}
 * instrumentation list if one is not present.
 *
 * @see DataLoader
 * @see DataLoaderRegistry
 */
@PublicApi
public class BatchInstrumentation extends SimplePerformantInstrumentation {


    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new BatchInstrumentationState(parameters.getExecutionInput().getDataLoaderRegistry());
    }


    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState rawState) {
        BatchInstrumentationState state = ofState(rawState);

        // make sure we have an AsyncExecutionStrategy
        ExecutionContext executionContext = parameters.getExecutionContext();
        OperationDefinition.Operation operation = executionContext.getOperationDefinition().getOperation();
        Assert.assertTrue(executionContext.getStrategy(operation) instanceof AsyncExecutionStrategy);

        DataLoaderRegistry finalRegistry = executionContext.getDataLoaderRegistry();
        state.setDataLoaderRegistry(finalRegistry);
        return noOp();
    }

    @Override
    public @Nullable ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState rawState) {
        BatchInstrumentationState state = ofState(rawState);
        return state.getApproach().beginExecutionStrategy(parameters, state.getState());
    }


    @Override
    public @Nullable InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState rawState) {
        BatchInstrumentationState state = ofState(rawState);
        return state.getApproach().beginFieldFetch(parameters, state.getState());
    }


    @Override
    public void completeValue(ExecutionContext executionContext, Object value,
                              GraphQLType fieldType,
                              ExecutionStrategyParameters parameters,
                              InstrumentationState rawState) {
        BatchInstrumentationState state = ofState(rawState);
        state.getApproach().completeValue(executionContext, value, fieldType, parameters, state.getState());
    }

    @Override
    public void finishedListCompletion(ExecutionContext executionContext, ExecutionStrategyParameters parameters, InstrumentationState rawState, int count) {
        BatchInstrumentationState state = ofState(rawState);
        state.getApproach().finishedListCompletion(executionContext, parameters, state.getState(), count);
    }
}
