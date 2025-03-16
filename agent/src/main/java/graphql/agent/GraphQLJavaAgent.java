package graphql.agent;

import graphql.agent.result.ExecutionTrackingResult;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.ResultPath;
import graphql.schema.DataFetchingEnvironment;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.DispatchResult;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static graphql.agent.result.ExecutionTrackingResult.DFResultType.DONE_CANCELLED;
import static graphql.agent.result.ExecutionTrackingResult.DFResultType.DONE_EXCEPTIONALLY;
import static graphql.agent.result.ExecutionTrackingResult.DFResultType.DONE_OK;
import static graphql.agent.result.ExecutionTrackingResult.DFResultType.PENDING;
import static graphql.agent.result.ExecutionTrackingResult.EXECUTION_TRACKING_KEY;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class GraphQLJavaAgent {


    public static final Map<ExecutionId, ExecutionTrackingResult> executionIdToData = new ConcurrentHashMap<>();
    public static final Map<DataLoader, ExecutionId> dataLoaderToExecutionId = new ConcurrentHashMap<>();

    public static void premain(String agentArgs, Instrumentation inst) {
        agentmain(agentArgs, inst);
    }


    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("GraphQL Java Agent is starting");
        new AgentBuilder.Default()
                .type(named("graphql.execution.Execution"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    return builder
                            .visit(Advice.to(ExecutionAdvice.class).on(nameMatches("executeOperation")));

                })
                .type(named("graphql.execution.ExecutionStrategy"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    return builder
                            .visit(Advice.to(DataFetcherInvokeAdvice.class).on(nameMatches("invokeDataFetcher")));
                })
                .type(named("org.dataloader.DataLoaderRegistry"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    return builder
                            .visit(Advice.to(DataLoaderRegistryAdvice.class).on(nameMatches("dispatchAll")));
                })
                .type(named("org.dataloader.DataLoader"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    return builder
                            .visit(Advice.to(DataLoaderLoadAdvice.class).on(nameMatches("load")));
                })
                .type(named("org.dataloader.DataLoaderHelper"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    return builder
                            .visit(Advice.to(DataLoaderHelperDispatchAdvice.class).on(nameMatches("dispatch")))
                            .visit(Advice.to(DataLoaderHelperInvokeBatchLoaderAdvice.class)
                                    .on(nameMatches("invokeLoader").and(takesArguments(List.class, List.class, List.class))));
                })
                .type(named("graphql.schema.DataFetchingEnvironmentImpl"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    return builder
                            .visit(Advice.to(DataFetchingEnvironmentAdvice.class).on(nameMatches("getDataLoader")));
                })
                .disableClassFormatChanges()
                .installOn(inst);

    }

    public static class ExecutionAdvice {

        public static class AfterExecutionHandler implements BiConsumer<Object, Throwable> {

            private final ExecutionContext executionContext;

            public AfterExecutionHandler(ExecutionContext executionContext) {
                this.executionContext = executionContext;
            }

            public void accept(Object o, Throwable throwable) {
                ExecutionId executionId = executionContext.getExecutionId();
                ExecutionTrackingResult executionTrackingResult = GraphQLJavaAgent.executionIdToData.get(executionId);
                executionTrackingResult.endExecutionTime.set(System.nanoTime());
                executionTrackingResult.endThread.set(Thread.currentThread().getName());
                executionContext.getGraphQLContext().put(EXECUTION_TRACKING_KEY, executionTrackingResult);
                // cleanup
                for (DataLoader<?, ?> dataLoader : executionTrackingResult.dataLoaderToName.keySet()) {
                    dataLoaderToExecutionId.remove(dataLoader);
                }
                executionIdToData.remove(executionId);

            }

        }


        @Advice.OnMethodEnter
        public static void executeOperationEnter(@Advice.Argument(0) ExecutionContext executionContext) {
            ExecutionTrackingResult executionTrackingResult = new ExecutionTrackingResult();
            executionTrackingResult.startExecutionTime.set(System.nanoTime());
            executionTrackingResult.startThread.set(Thread.currentThread().getName());
            executionContext.getGraphQLContext().put(EXECUTION_TRACKING_KEY, new ExecutionTrackingResult());

            GraphQLJavaAgent.executionIdToData.put(executionContext.getExecutionId(), executionTrackingResult);

            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            for (String name : dataLoaderRegistry.getDataLoadersMap().keySet()) {
                DataLoader dataLoader = dataLoaderRegistry.getDataLoader(name);
                GraphQLJavaAgent.dataLoaderToExecutionId.put(dataLoader, executionContext.getExecutionId());
                executionTrackingResult.dataLoaderToName.put(dataLoader, name);
            }
        }

        @Advice.OnMethodExit
        public static void executeOperationExit(@Advice.Argument(0) ExecutionContext executionContext,
                                                @Advice.Return(typing = Assigner.Typing.DYNAMIC) CompletableFuture<Object> result) {

            result.whenComplete(new AfterExecutionHandler(executionContext));
        }
    }

    public static class DataFetcherInvokeAdvice {

        public static class DataFetcherFinishedHandler implements BiConsumer<Object, Throwable> {

            private final ExecutionContext executionContext;
            private final ExecutionStrategyParameters parameters;
            private final long startTime;

            public DataFetcherFinishedHandler(ExecutionContext executionContext, ExecutionStrategyParameters parameters, long startTime) {
                this.executionContext = executionContext;
                this.parameters = parameters;
                this.startTime = startTime;
            }

            @Override
            public void accept(Object o, Throwable throwable) {
                ExecutionId executionId = executionContext.getExecutionId();
                ExecutionTrackingResult executionTrackingResult = GraphQLJavaAgent.executionIdToData.get(executionId);
                ResultPath path = parameters.getPath();
                executionTrackingResult.finishedTimePerPath.put(path, System.nanoTime() - startTime);
                executionTrackingResult.finishedThreadPerPath.put(path, Thread.currentThread().getName());
            }
        }

        @Advice.OnMethodEnter
        public static void invokeDataFetcherEnter(@Advice.Argument(0) ExecutionContext executionContext,
                                                  @Advice.Argument(1) ExecutionStrategyParameters parameters) {
            ExecutionTrackingResult executionTrackingResult = GraphQLJavaAgent.executionIdToData.get(executionContext.getExecutionId());
            executionTrackingResult.start(parameters.getPath(), System.nanoTime());
            executionTrackingResult.startInvocationThreadPerPath.put(parameters.getPath(), Thread.currentThread().getName());
        }

        @Advice.OnMethodExit
        public static void invokeDataFetcherExit(@Advice.Argument(0) ExecutionContext executionContext,
                                                 @Advice.Argument(1) ExecutionStrategyParameters parameters,
                                                 @Advice.Return(readOnly = false) Object cfOrObject) {
            // ExecutionTrackingResult executionTrackingResult = executionContext.getGraphQLContext().get(EXECUTION_TRACKING_KEY);
            ExecutionTrackingResult executionTrackingResult = GraphQLJavaAgent.executionIdToData.get(executionContext.getExecutionId());
            ResultPath path = parameters.getPath();
            long startTime = executionTrackingResult.timePerPath.get(path);
            executionTrackingResult.end(path, System.nanoTime());
            if (cfOrObject instanceof CompletableFuture) {
                CompletableFuture<Object> result = (CompletableFuture<Object>) cfOrObject;
                if (result.isDone()) {
                    if (result.isCancelled()) {
                        executionTrackingResult.setDfResultTypes(path, DONE_CANCELLED);
                    } else if (result.isCompletedExceptionally()) {
                        executionTrackingResult.setDfResultTypes(path, DONE_EXCEPTIONALLY);
                    } else {
                        executionTrackingResult.setDfResultTypes(path, DONE_OK);
                    }
                } else {
                    executionTrackingResult.setDfResultTypes(path, PENDING);
                }
                // overriding the result to make sure the finished handler is called first when the DF is finished
                // otherwise it is a completion tree instead of chain
                cfOrObject = result.whenComplete(new DataFetcherFinishedHandler(executionContext, parameters, startTime));
            } else {
                // materialized value - not a CF
                executionTrackingResult.setDfResultTypes(path, DONE_OK);
                new DataFetcherFinishedHandler(executionContext, parameters, startTime).accept(cfOrObject, null);
            }
        }

    }


    public static class DataLoaderHelperInvokeBatchLoaderAdvice {

        @Advice.OnMethodEnter
        public static void invokeLoader(@Advice.Argument(0) List keys,
                                        @Advice.Argument(1) List keysContext,
                                        @Advice.Argument(2) List queuedFutures,
                                        @Advice.This(typing = Assigner.Typing.DYNAMIC) Object dataLoaderHelper) {
            DataLoader dataLoader = getDataLoaderForHelper(dataLoaderHelper);
            ExecutionId executionId = GraphQLJavaAgent.dataLoaderToExecutionId.get(dataLoader);
            ExecutionTrackingResult executionTrackingResult = GraphQLJavaAgent.executionIdToData.get(executionId);
            String dataLoaderName = executionTrackingResult.dataLoaderToName.get(dataLoader);

            synchronized (executionTrackingResult.dataLoaderNameToBatchCall) {
                executionTrackingResult.dataLoaderNameToBatchCall.putIfAbsent(dataLoaderName, new ArrayList<>());
                executionTrackingResult.dataLoaderNameToBatchCall.get(dataLoaderName)
                        .add(new ExecutionTrackingResult.BatchLoadingCall(keys.size(), Thread.currentThread().getName()));
            }

        }
    }

    public static class DataLoaderHelperDispatchAdvice {

        @Advice.OnMethodExit
        public static void dispatch(@Advice.This(typing = Assigner.Typing.DYNAMIC) Object dataLoaderHelper,
                                    @Advice.Return(typing = Assigner.Typing.DYNAMIC) DispatchResult dispatchResult) {
            try {
                // System.out.println("dataloader helper Dispatch " + dataLoaderHelper + " load for execution " + dispatchResult);
                // DataLoader dataLoader = getDataLoaderForHelper(dataLoaderHelper);
                // // System.out.println("dataLoader: " + dataLoader);
                // ExecutionId executionId = GraphQLJavaAgent.dataLoaderToExecutionId.get(dataLoader);
                // ExecutionTrackingResult ExecutionTrackingResult = GraphQLJavaAgent.executionIdToData.get(executionId);
                // String dataLoaderName = ExecutionTrackingResult.dataLoaderToName.get(dataLoader);
                //
                // ExecutionTrackingResult.dataLoaderNameToBatchCall.putIfAbsent(dataLoaderName, new ArrayList<>());
                // ExecutionTrackingResult.dataLoaderNameToBatchCall.get(dataLoaderName).add(new ExecutionTrackingResult.BatchLoadingCall(dispatchResult.getKeysCount()));

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    public static DataLoader getDataLoaderForHelper(Object dataLoaderHelper) {
        try {
            Field field = dataLoaderHelper.getClass().getDeclaredField("dataLoader");
            field.setAccessible(true);
            return (DataLoader) field.get(dataLoaderHelper);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


}

class DataFetchingEnvironmentAdvice {


    @Advice.OnMethodExit
    public static void getDataLoader(@Advice.Argument(0) String dataLoaderName,
                                     @Advice.This(typing = Assigner.Typing.DYNAMIC) DataFetchingEnvironment dataFetchingEnvironment,
                                     @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) DataLoader dataLoader) {
        ExecutionTrackingResult executionTrackingResult = GraphQLJavaAgent.executionIdToData.get(dataFetchingEnvironment.getExecutionId());
        ResultPath resultPath = dataFetchingEnvironment.getExecutionStepInfo().getPath();
        executionTrackingResult.resultPathToDataLoaderUsed.put(resultPath, dataLoaderName);

    }

}


class DataLoaderLoadAdvice {

    @Advice.OnMethodEnter
    public static void load(@Advice.This(typing = Assigner.Typing.DYNAMIC) Object dataLoader) {
        ExecutionId executionId = GraphQLJavaAgent.dataLoaderToExecutionId.get(dataLoader);
        String dataLoaderName = GraphQLJavaAgent.executionIdToData.get(executionId).dataLoaderToName.get(dataLoader);
    }

}

class DataLoaderRegistryAdvice {

    @Advice.OnMethodEnter
    public static void dispatchAll(@Advice.This(typing = Assigner.Typing.DYNAMIC) Object dataLoaderRegistry) {
        List<DataLoader<?, ?>> dataLoaders = ((DataLoaderRegistry) dataLoaderRegistry).getDataLoaders();
        ExecutionId executionId = GraphQLJavaAgent.dataLoaderToExecutionId.get(dataLoaders.get(0));
    }

}



