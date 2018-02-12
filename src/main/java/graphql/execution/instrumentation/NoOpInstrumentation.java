package graphql.execution.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreatePreExecutionStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionContextParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionResultParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation of {@link graphql.execution.instrumentation.Instrumentation} that does nothing.  It can be used
 * as a base for derived classes where you only implement the methods you want to
 */
@PublicApi
public class NoOpInstrumentation implements Instrumentation {

    /**
     * A singleton instance of a {@link graphql.execution.instrumentation.Instrumentation} that does nothing
     */
    public static final NoOpInstrumentation INSTANCE = new NoOpInstrumentation();

    public NoOpInstrumentation() {
    }

    @Override
    public InstrumentationPreExecutionState createPreExecutionState(InstrumentationCreatePreExecutionStateParameters parameters) {
        return null;
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return null;
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginDataFetchDispatch(InstrumentationDataFetchParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }


    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteField(InstrumentationFieldCompleteParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<Map<String, List<Field>>> beginFields(InstrumentationExecutionStrategyParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteFieldList(InstrumentationFieldCompleteParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        return schema;
    }

    @Override
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionContextParameters parameters) {
        return executionContext;
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        return dataFetcher;
    }

    @Override
    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        return executionInput;
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionResultParameters parameters) {
        return CompletableFuture.completedFuture(executionResult);
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentFinalExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        return CompletableFuture.completedFuture(executionResult);
    }

    public static class NoOpInstrumentationContext<T> implements InstrumentationContext<T> {
        @Override
        public void onEnd(T result, Throwable t) {
        }
    }
}
