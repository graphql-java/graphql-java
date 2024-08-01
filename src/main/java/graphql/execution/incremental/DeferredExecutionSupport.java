package graphql.execution.incremental;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.instrumentation.Instrumentation;
import graphql.incremental.IncrementalPayload;
import graphql.util.FpKit;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The purpose of this class hierarchy is to encapsulate most of the logic for deferring field execution, thus
 * keeping the main execution strategy code clean and focused on the main execution logic.
 * <p>
 * The {@link NoOp} instance should be used when incremental support is not enabled for the current execution. The
 * methods in this class will return empty or no-op results, that should not impact the main execution.
 * <p>
 * {@link DeferredExecutionSupportImpl} is the actual implementation that will be used when incremental support is enabled.
 */
@Internal
public interface DeferredExecutionSupport {

    boolean isDeferredField(MergedField mergedField);

    int deferredFieldsCount();

    List<String> getNonDeferredFieldNames(List<String> allFieldNames);

    Set<IncrementalCall<? extends IncrementalPayload>> createCalls(ExecutionStrategyParameters executionStrategyParameters);

    DeferredExecutionSupport NOOP = new DeferredExecutionSupport.NoOp();

    /**
     * An implementation that actually executes the deferred fields.
     */
    class DeferredExecutionSupportImpl implements DeferredExecutionSupport {
        private final ImmutableListMultimap<DeferredExecution, MergedField> deferredExecutionToFields;
        private final ImmutableSet<MergedField> deferredFields;
        private final ImmutableList<String> nonDeferredFieldNames;
        private final ExecutionStrategyParameters parameters;
        private final ExecutionContext executionContext;
        private final BiFunction<ExecutionContext, ExecutionStrategyParameters, CompletableFuture<FieldValueInfo>> resolveFieldWithInfoFn;
        private final Map<String, Supplier<CompletableFuture<DeferredFragmentCall.FieldWithExecutionResult>>> dfCache = new HashMap<>();

        public DeferredExecutionSupportImpl(
                MergedSelectionSet mergedSelectionSet,
                ExecutionStrategyParameters parameters,
                ExecutionContext executionContext,
                BiFunction<ExecutionContext, ExecutionStrategyParameters, CompletableFuture<FieldValueInfo>> resolveFieldWithInfoFn
        ) {
            this.executionContext = executionContext;
            this.resolveFieldWithInfoFn = resolveFieldWithInfoFn;
            ImmutableListMultimap.Builder<DeferredExecution, MergedField> deferredExecutionToFieldsBuilder = ImmutableListMultimap.builder();
            ImmutableSet.Builder<MergedField> deferredFieldsBuilder = ImmutableSet.builder();
            ImmutableList.Builder<String> nonDeferredFieldNamesBuilder = ImmutableList.builder();

            mergedSelectionSet.getSubFields().values().forEach(mergedField -> {
                mergedField.getDeferredExecutions().forEach(de -> {
                    deferredExecutionToFieldsBuilder.put(de, mergedField);
                    deferredFieldsBuilder.add(mergedField);
                });

                if (mergedField.getDeferredExecutions().isEmpty()) {
                    nonDeferredFieldNamesBuilder.add(mergedField.getSingleField().getResultKey());
                }
            });

            this.deferredExecutionToFields = deferredExecutionToFieldsBuilder.build();
            this.deferredFields = deferredFieldsBuilder.build();
            this.parameters = parameters;
            this.nonDeferredFieldNames = nonDeferredFieldNamesBuilder.build();
        }

        @Override
        public boolean isDeferredField(MergedField mergedField) {
            return deferredFields.contains(mergedField);
        }

        @Override
        public int deferredFieldsCount() {
            return deferredFields.size();
        }

        @Override
        public List<String> getNonDeferredFieldNames(List<String> allFieldNames) {
            return this.nonDeferredFieldNames;
        }

        @Override
        public Set<IncrementalCall<? extends IncrementalPayload>> createCalls(ExecutionStrategyParameters executionStrategyParameters) {
            return deferredExecutionToFields.keySet().stream()
                    .map(deferredExecution -> this.createDeferredFragmentCall(deferredExecution, executionStrategyParameters))
                    .collect(Collectors.toSet());
        }

        private DeferredFragmentCall createDeferredFragmentCall(DeferredExecution deferredExecution, ExecutionStrategyParameters executionStrategyParameters) {
            DeferredCallContext deferredCallContext = new DeferredCallContext();

            List<MergedField> mergedFields = deferredExecutionToFields.get(deferredExecution);

            List<Supplier<CompletableFuture<DeferredFragmentCall.FieldWithExecutionResult>>> calls = mergedFields.stream()
                    .map(currentField -> this.createResultSupplier(currentField, deferredCallContext, executionStrategyParameters))
                    .collect(Collectors.toList());

            return new DeferredFragmentCall(
                    deferredExecution.getLabel(),
                    this.parameters.getPath(),
                    calls,
                    deferredCallContext
            );
        }

        private Supplier<CompletableFuture<DeferredFragmentCall.FieldWithExecutionResult>> createResultSupplier(
                MergedField currentField,
                DeferredCallContext deferredCallContext,
                ExecutionStrategyParameters executionStrategyParameters
        ) {
            Map<String, MergedField> fields = new LinkedHashMap<>();
            fields.put(currentField.getResultKey(), currentField);

            ExecutionStrategyParameters callParameters = parameters.transform(builder ->
                    {
                        MergedSelectionSet mergedSelectionSet = MergedSelectionSet.newMergedSelectionSet().subFields(fields).build();
                        builder.deferredCallContext(deferredCallContext)
                                .field(currentField)
                                .fields(mergedSelectionSet)
                                .path(parameters.getPath().segment(currentField.getResultKey()))
                                .parent(null); // this is a break in the parent -> child chain - it's a new start effectively
                    }
            );


            Instrumentation instrumentation = executionContext.getInstrumentation();

            instrumentation.beginDeferredField(executionContext.getInstrumentationState());

            return dfCache.computeIfAbsent(
                    currentField.getResultKey(),
                    // The same field can be associated with multiple defer executions, so
                    // we memoize the field resolution to avoid multiple calls to the same data fetcher
                    key -> FpKit.interThreadMemoize(() -> {
                                CompletableFuture<FieldValueInfo> fieldValueResult = resolveFieldWithInfoFn
                                        .apply(executionContext, callParameters);

                                CompletableFuture<ExecutionResult> executionResultCF = fieldValueResult
                                        .thenCompose(fvi -> {
                                                    executionContext.getDataLoaderDispatcherStrategy().executeDeferredOnFieldValueInfo(fvi, executionStrategyParameters);

                                                    return fvi
                                                            .getFieldValueFuture()
                                                            .thenApply(fv -> ExecutionResultImpl.newExecutionResult().data(fv).build());
                                                }
                                        );

                                return executionResultCF
                                        .thenApply(executionResult ->
                                                new DeferredFragmentCall.FieldWithExecutionResult(currentField.getResultKey(), executionResult)
                                        );
                            }
                    )
            );
        }
    }

    /**
     * A no-op implementation that should be used when incremental support is not enabled for the current execution.
     */
    class NoOp implements DeferredExecutionSupport {

        @Override
        public boolean isDeferredField(MergedField mergedField) {
            return false;
        }

        @Override
        public int deferredFieldsCount() {
            return 0;
        }

        @Override
        public List<String> getNonDeferredFieldNames(List<String> allFieldNames) {
            return allFieldNames;
        }

        @Override
        public Set<IncrementalCall<? extends IncrementalPayload>> createCalls(ExecutionStrategyParameters executionStrategyParameters) {
            return Collections.emptySet();
        }
    }
}
