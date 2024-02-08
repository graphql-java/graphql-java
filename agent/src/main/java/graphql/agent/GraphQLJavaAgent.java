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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static graphql.agent.GraphQLJavaAgent.ExecutionData.DFResultType.DONE_CANCELLED;
import static graphql.agent.GraphQLJavaAgent.ExecutionData.DFResultType.DONE_EXCEPTIONALLY;
import static graphql.agent.GraphQLJavaAgent.ExecutionData.DFResultType.DONE_OK;
import static graphql.agent.GraphQLJavaAgent.ExecutionData.DFResultType.PENDING;
import static graphql.agent.result.ExecutionTrackingResult.EXECUTION_TRACKING_KEY;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class GraphQLJavaAgent {

    public static class ExecutionData {
        public final AtomicReference<String> startThread = new AtomicReference<>();
        public final AtomicReference<String> endThread = new AtomicReference<>();
        public final AtomicLong startExecutionTime = new AtomicLong();
        public final AtomicLong endExecutionTime = new AtomicLong();
        public final Map<ResultPath, String> resultPathToDataLoaderUsed = new ConcurrentHashMap<>();
        public final Map<DataLoader, String> dataLoaderToName = new ConcurrentHashMap<>();

        public final Map<ResultPath, Long> timePerPath = new ConcurrentHashMap<>();
        public final Map<ResultPath, Long> finishedTimePerPath = new ConcurrentHashMap<>();
        public final Map<ResultPath, String> finishedThreadPerPath = new ConcurrentHashMap<>();
        public final Map<ResultPath, String> startInvocationThreadPerPath = new ConcurrentHashMap<>();
        private final Map<ResultPath, DFResultType> dfResultTypes = new ConcurrentHashMap<>();

        public static class BatchLoadingCall {
            public BatchLoadingCall(int resultCount) {
                this.resultCount = resultCount;
            }

            public final int resultCount;
        }

        public final Map<String, List<BatchLoadingCall>> dataLoaderNameToBatchCall = new ConcurrentHashMap<>();

        public String print(String executionId) {
            StringBuilder s = new StringBuilder();
            s.append("==========================").append("\n");
            s.append("Summary for execution with id ").append(executionId).append("\n");
            s.append("==========================").append("\n");
            s.append("Execution time in ms:").append((endExecutionTime.get() - startExecutionTime.get()) / 1_000_000L).append("\n");
            s.append("Fields count: ").append(timePerPath.keySet().size()).append("\n");
            s.append("Blocking fields count: ").append(dfResultTypes.values().stream().filter(dfResultType -> dfResultType != PENDING).count()).append("\n");
            s.append("Nonblocking fields count: ").append(dfResultTypes.values().stream().filter(dfResultType -> dfResultType == PENDING).count()).append("\n");
            s.append("DataLoaders used: ").append(dataLoaderToName.size()).append("\n");
            s.append("DataLoader names: ").append(dataLoaderToName.values()).append("\n");
            s.append("start execution thread: '").append(startThread.get()).append("'\n");
            s.append("end execution  thread: '").append(endThread.get()).append("'\n");
            s.append("BatchLoader calls details: ").append("\n");
            s.append("==========================").append("\n");
            for (String dataLoaderName : dataLoaderNameToBatchCall.keySet()) {
                s.append("DataLoader: '").append(dataLoaderName).append("' called ").append(dataLoaderNameToBatchCall.get(dataLoaderName).size()).append(" times, ").append("\n");
                List<ResultPath> resultPathUsed = new ArrayList<>();
                for (ResultPath resultPath : resultPathToDataLoaderUsed.keySet()) {
                    if (resultPathToDataLoaderUsed.get(resultPath).equals(dataLoaderName)) {
                        resultPathUsed.add(resultPath);
                    }
                }
                s.append("DataLoader: '").append(dataLoaderName).append("' used in fields: ").append(resultPathUsed).append("\n");
                for (BatchLoadingCall batchLoadingCall : dataLoaderNameToBatchCall.get(dataLoaderName)) {
                    s.append("Batch call with ").append(batchLoadingCall.resultCount).append(" results").append("\n");
                }
            }
            s.append("Field details:").append("\n");
            s.append("===============").append("\n");
            for (ResultPath path : timePerPath.keySet()) {
                s.append("Field: '").append(path).append("'\n");
                s.append("invocation time: ").append(timePerPath.get(path)).append(" nano seconds, ").append("\n");
                s.append("completion time: ").append(finishedTimePerPath.get(path)).append(" nano seconds, ").append("\n");
                s.append("Result type: ").append(dfResultTypes.get(path)).append("\n");
                s.append("invoked in thread: ").append(startInvocationThreadPerPath.get(path)).append("\n");
                s.append("finished in thread: ").append(finishedThreadPerPath.get(path)).append("\n");
                s.append("-------------\n");
            }
            s.append("==========================").append("\n");
            s.append("==========================").append("\n");
            return s.toString();

        }

        @Override
        public String toString() {
            return "ExecutionData{" +
                "resultPathToDataLoaderUsed=" + resultPathToDataLoaderUsed +
                ", dataLoaderNames=" + dataLoaderToName.values() +
                ", timePerPath=" + timePerPath +
                ", dfResultTypes=" + dfResultTypes +
                '}';
        }

        public enum DFResultType {
            DONE_OK,
            DONE_EXCEPTIONALLY,
            DONE_CANCELLED,
            PENDING,
        }


        public void start(ResultPath path, long startTime) {
            timePerPath.put(path, startTime);
        }

        public void end(ResultPath path, long endTime) {
            timePerPath.put(path, endTime - timePerPath.get(path));
        }

        public int dataFetcherCount() {
            return timePerPath.size();
        }

        public long getTime(ResultPath path) {
            return timePerPath.get(path);
        }

        public long getTime(String path) {
            return timePerPath.get(ResultPath.parse(path));
        }

        public void setDfResultTypes(ResultPath resultPath, DFResultType resultTypes) {
            dfResultTypes.put(resultPath, resultTypes);
        }

        public DFResultType getDfResultTypes(ResultPath resultPath) {
            return dfResultTypes.get(resultPath);
        }

        public DFResultType getDfResultTypes(String resultPath) {
            return dfResultTypes.get(ResultPath.parse(resultPath));
        }


    }

    public static final Map<ExecutionId, ExecutionData> executionIdToData = new ConcurrentHashMap<>();
    public static final Map<DataLoader, ExecutionId> dataLoaderToExecutionId = new ConcurrentHashMap<>();

    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("Agent is running");
        new AgentBuilder.Default()
            .type(named("graphql.execution.Execution"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                // ClassInjector.UsingInstrumentation.of()
                // System.out.println("transforming " + typeDescription);
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
                // System.out.println("transforming " + typeDescription);
                return builder
                    .visit(Advice.to(DataLoaderRegistryAdvice.class).on(nameMatches("dispatchAll")));
            })
            .type(named("org.dataloader.DataLoader"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                // System.out.println("transforming " + typeDescription);
                return builder
                    .visit(Advice.to(DataLoaderLoadAdvice.class).on(nameMatches("load")));
            })
            .type(named("org.dataloader.DataLoaderHelper"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                // System.out.println("transforming " + typeDescription);
                return builder
                    .visit(Advice.to(DataLoaderHelperDispatchAdvice.class).on(nameMatches("dispatch")));
            })
            .type(named("graphql.schema.DataFetchingEnvironmentImpl"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                // System.out.println("transforming " + typeDescription);
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
                GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionIdToData.get(executionId);
                executionData.endExecutionTime.set(System.nanoTime());
                executionData.endThread.set(Thread.currentThread().getName());
                System.out.println("execution finished for: " + executionId + " with data " + executionData);
                System.out.println(executionData.print(executionId.toString()));
            }

        }


        @Advice.OnMethodEnter
        public static void executeOperationEnter(@Advice.Argument(0) ExecutionContext executionContext) {
            GraphQLJavaAgent.ExecutionData executionData = new GraphQLJavaAgent.ExecutionData();
            executionData.startExecutionTime.set(System.nanoTime());
            executionData.startThread.set(Thread.currentThread().getName());
            System.out.println("execution started for: " + executionContext.getExecutionId());
            executionContext.getGraphQLContext().put(EXECUTION_TRACKING_KEY, new ExecutionTrackingResult());

            GraphQLJavaAgent.executionIdToData.put(executionContext.getExecutionId(), executionData);

            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            for (String name : dataLoaderRegistry.getDataLoadersMap().keySet()) {
                DataLoader dataLoader = dataLoaderRegistry.getDataLoader(name);
                GraphQLJavaAgent.dataLoaderToExecutionId.put(dataLoader, executionContext.getExecutionId());
                executionData.dataLoaderToName.put(dataLoader, name);
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
                GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionIdToData.get(executionId);
                ResultPath path = parameters.getPath();
                System.out.println("finished " + path);
                executionData.finishedTimePerPath.put(path, System.nanoTime() - startTime);
                executionData.finishedThreadPerPath.put(path, Thread.currentThread().getName());
            }
        }

        @Advice.OnMethodEnter
        public static void invokeDataFetcherEnter(@Advice.Argument(0) ExecutionContext executionContext,
                                                  @Advice.Argument(1) ExecutionStrategyParameters parameters) {
            GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionIdToData.get(executionContext.getExecutionId());
            executionData.start(parameters.getPath(), System.nanoTime());
            executionData.startInvocationThreadPerPath.put(parameters.getPath(), Thread.currentThread().getName());
        }

        @Advice.OnMethodExit
        public static void invokeDataFetcherExit(@Advice.Argument(0) ExecutionContext executionContext,
                                                 @Advice.Argument(1) ExecutionStrategyParameters parameters,
                                                 @Advice.Return(readOnly = false) CompletableFuture<Object> result) {
            // ExecutionTrackingResult executionTrackingResult = executionContext.getGraphQLContext().get(EXECUTION_TRACKING_KEY);
            GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionIdToData.get(executionContext.getExecutionId());
            ResultPath path = parameters.getPath();
            long startTime = executionData.timePerPath.get(path);
            executionData.end(path, System.nanoTime());
            if (result.isDone()) {
                if (result.isCancelled()) {
                    executionData.setDfResultTypes(path, DONE_CANCELLED);
                } else if (result.isCompletedExceptionally()) {
                    executionData.setDfResultTypes(path, DONE_EXCEPTIONALLY);
                } else {
                    executionData.setDfResultTypes(path, DONE_OK);
                }
            } else {
                executionData.setDfResultTypes(path, PENDING);
            }
            // overriding the result to make sure the finished handler is called first when the DF is finished
            // otherwise it is a completion tree instead of chain
            result = result.whenComplete(new DataFetcherFinishedHandler(executionContext, parameters, startTime));
        }

    }

}

class DataFetchingEnvironmentAdvice {


    @Advice.OnMethodExit
    public static void getDataLoader(@Advice.Argument(0) String dataLoaderName,
                                     @Advice.This(typing = Assigner.Typing.DYNAMIC) DataFetchingEnvironment dataFetchingEnvironment,
                                     @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) DataLoader dataLoader) {
        GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionIdToData.get(dataFetchingEnvironment.getExecutionId());
        // System.out.println("execution data: " + executionData);
        ResultPath resultPath = dataFetchingEnvironment.getExecutionStepInfo().getPath();
        executionData.resultPathToDataLoaderUsed.put(resultPath, dataLoaderName);

        // System.out.println(dataLoaderName + " > " + dataLoader);
    }

}

class DataLoaderHelperDispatchAdvice {

    @Advice.OnMethodExit
    public static void dispatch(@Advice.This(typing = Assigner.Typing.DYNAMIC) Object dataLoaderHelper,
                                @Advice.Return(typing = Assigner.Typing.DYNAMIC) DispatchResult dispatchResult) {
        try {
            // System.out.println("dataloader helper Dispatch " + dataLoaderHelper + " load for execution " + dispatchResult);
            Field field = dataLoaderHelper.getClass().getDeclaredField("dataLoader");
            field.setAccessible(true);
            DataLoader dataLoader = (DataLoader) field.get(dataLoaderHelper);
            // System.out.println("dataLoader: " + dataLoader);
            ExecutionId executionId = GraphQLJavaAgent.dataLoaderToExecutionId.get(dataLoader);
            GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionIdToData.get(executionId);
            String dataLoaderName = executionData.dataLoaderToName.get(dataLoader);

            executionData.dataLoaderNameToBatchCall.putIfAbsent(dataLoaderName, new ArrayList<>());
            executionData.dataLoaderNameToBatchCall.get(dataLoaderName).add(new GraphQLJavaAgent.ExecutionData.BatchLoadingCall(dispatchResult.getKeysCount()));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}

class DataLoaderLoadAdvice {

    @Advice.OnMethodEnter
    public static void load(@Advice.This(typing = Assigner.Typing.DYNAMIC) Object dataLoader) {
        ExecutionId executionId = GraphQLJavaAgent.dataLoaderToExecutionId.get(dataLoader);
        String dataLoaderName = GraphQLJavaAgent.executionIdToData.get(executionId).dataLoaderToName.get(dataLoader);
        // System.out.println("dataloader " + dataLoaderName + " load for execution " + executionId);
    }

}

class DataLoaderRegistryAdvice {

    @Advice.OnMethodEnter
    public static void dispatchAll(@Advice.This(typing = Assigner.Typing.DYNAMIC) Object dataLoaderRegistry) {
        List<DataLoader<?, ?>> dataLoaders = ((DataLoaderRegistry) dataLoaderRegistry).getDataLoaders();
        ExecutionId executionId = GraphQLJavaAgent.dataLoaderToExecutionId.get(dataLoaders.get(0));
        System.out.println("calling dispatchAll for " + executionId);
    }

}


