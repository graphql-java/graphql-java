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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static graphql.agent.GraphQLJavaAgent.ExecutionData.DFResultType.DONE_CANCELLED;
import static graphql.agent.GraphQLJavaAgent.ExecutionData.DFResultType.DONE_EXCEPTIONALLY;
import static graphql.agent.GraphQLJavaAgent.ExecutionData.DFResultType.DONE_OK;
import static graphql.agent.GraphQLJavaAgent.ExecutionData.DFResultType.PENDING;
import static graphql.agent.result.ExecutionTrackingResult.EXECUTION_TRACKING_KEY;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class GraphQLJavaAgent {

    public static class ExecutionData {
        public Map<ResultPath, String> resultPathToDataLoaderUsed = new ConcurrentHashMap<>();
        public Map<DataLoader, String> dataLoaderToName = new ConcurrentHashMap<>();

        private final Map<ResultPath, Long> timePerPath = new ConcurrentHashMap<>();
        private final Map<ResultPath, DFResultType> dfResultTypes = new ConcurrentHashMap<>();

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
            .installOn(inst);

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
            String dataLoaderName = GraphQLJavaAgent.executionIdToData.get(executionId).dataLoaderToName.get(dataLoader);

            System.out.println("dataloader " + dataLoaderName + " dispatch result size:" + dispatchResult.getKeysCount());

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

class ExecutionAdvice {

    @Advice.OnMethodEnter
    public static void executeOperationEnter(@Advice.Argument(0) ExecutionContext executionContext) {
        System.out.println("execution started for: " + executionContext.getExecutionId());
        executionContext.getGraphQLContext().put(EXECUTION_TRACKING_KEY, new ExecutionTrackingResult());
        GraphQLJavaAgent.ExecutionData executionData = new GraphQLJavaAgent.ExecutionData();
        GraphQLJavaAgent.executionIdToData.put(executionContext.getExecutionId(), executionData);

        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        for (String name : dataLoaderRegistry.getDataLoadersMap().keySet()) {
            DataLoader dataLoader = dataLoaderRegistry.getDataLoader(name);
            GraphQLJavaAgent.dataLoaderToExecutionId.put(dataLoader, executionContext.getExecutionId());
            executionData.dataLoaderToName.put(dataLoader, name);
        }
    }

    @Advice.OnMethodExit
    public static void executeOperationExit(@Advice.Argument(0) ExecutionContext executionContext) {
        ExecutionId executionId = executionContext.getExecutionId();
        System.out.println("execution finished for: " + executionId + " with data " + GraphQLJavaAgent.executionIdToData.get(executionId));
            // cleanup
            // GraphQLJavaAgent.executionDataMap.get(executionId).dataLoaderToName.forEach((dataLoader, s) -> {
            //   GraphQLJavaAgent.dataLoaderToExecutionId.remove(dataLoader);
            // });
            // GraphQLJavaAgent.executionDataMap.remove(executionContext.getExecutionId());
    }
}

class DataFetcherInvokeAdvice {
    @Advice.OnMethodEnter
    public static void invokeDataFetcherEnter(@Advice.Argument(0) ExecutionContext executionContext,
                                              @Advice.Argument(1) ExecutionStrategyParameters parameters) {
        GraphQLJavaAgent.executionIdToData.get(executionContext.getExecutionId())
            .start(parameters.getPath(), System.nanoTime());
    }

    @Advice.OnMethodExit
    public static void invokeDataFetcherExit(@Advice.Argument(0) ExecutionContext executionContext,
                                             @Advice.Argument(1) ExecutionStrategyParameters parameters,
                                             @Advice.Return CompletableFuture<Object> result) {
        // ExecutionTrackingResult executionTrackingResult = executionContext.getGraphQLContext().get(EXECUTION_TRACKING_KEY);
        GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionIdToData.get(executionContext.getExecutionId());
        ResultPath path = parameters.getPath();
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
    }

}
