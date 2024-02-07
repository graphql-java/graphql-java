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

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static graphql.agent.result.ExecutionTrackingResult.EXECUTION_TRACKING_KEY;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class GraphQLJavaAgent {

    public static class ExecutionData {
        public Set<ResultPath> resultPaths = ConcurrentHashMap.newKeySet();

        public Map<ResultPath, DataLoader> resultPathToDataLoader = new ConcurrentHashMap<>();
        public Map<DataLoader, String> dataLoaderToName = new ConcurrentHashMap<>();

        @Override
        public String toString() {
            return "ExecutionData{" +
                "resultPathToDataLoader=" + resultPathToDataLoader +
                ", dataLoaderToName=" + dataLoaderToName +
                '}';
        }
    }

    public static final Map<ExecutionId, ExecutionData> executionDataMap = new ConcurrentHashMap<>();
    public static final Map<DataLoader, ExecutionId> dataLoaderToExecutionId = new ConcurrentHashMap<>();

    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("Agent is running");
        new AgentBuilder.Default()
            .type(named("graphql.execution.Execution"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                System.out.println("transforming " + typeDescription);
                return builder
                    .visit(Advice.to(ExecutionAdvice.class).on(nameMatches("executeOperation")));

            })
            .type(named("graphql.execution.ExecutionStrategy"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                System.out.println("Transforming " + typeDescription);
                return builder
                    .visit(Advice.to(DataFetcherInvokeAdvice.class).on(nameMatches("invokeDataFetcher")));
            })
            .type(named("org.dataloader.DataLoaderRegistry"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                System.out.println("transforming " + typeDescription);
                return builder
                    .visit(Advice.to(DataLoaderRegistryAdvice.class).on(nameMatches("dispatchAll")));
            })
            .type(named("org.dataloader.DataLoader"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                System.out.println("transforming " + typeDescription);
                return builder
                    .visit(Advice.to(DataLoaderAdvice.class).on(nameMatches("load")));
            })
            .type(named("graphql.schema.DataFetchingEnvironmentImpl"))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                System.out.println("transforming " + typeDescription);
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
        GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionDataMap.get(dataFetchingEnvironment.getExecutionId());
        System.out.println("execution data: " + executionData);
        executionData.resultPathToDataLoader.put(dataFetchingEnvironment.getExecutionStepInfo().getPath(), dataLoader);
        executionData.dataLoaderToName.put(dataLoader, dataLoaderName);
        GraphQLJavaAgent.dataLoaderToExecutionId.put(dataLoader, dataFetchingEnvironment.getExecutionId());

        System.out.println(dataLoaderName + " > " + dataLoader);
    }

}


class DataLoaderAdvice {

    @Advice.OnMethodEnter
    public static void load(@Advice.This(typing = Assigner.Typing.DYNAMIC) Object dataLoader) {
        ExecutionId executionId = GraphQLJavaAgent.dataLoaderToExecutionId.get(dataLoader);
        String dataLoaderName = GraphQLJavaAgent.executionDataMap.get(executionId).dataLoaderToName.get(dataLoader);
        System.out.println("dataloader " + dataLoaderName + " load for execution " + executionId);
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
        GraphQLJavaAgent.executionDataMap.put(executionContext.getExecutionId(), new GraphQLJavaAgent.ExecutionData());
    }

    @Advice.OnMethodExit
    public static void executeOperationExit(@Advice.Argument(0) ExecutionContext executionContext) {
        ExecutionId executionId = executionContext.getExecutionId();
        System.out.println("execution finished for: " + executionId);
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

        ExecutionTrackingResult executionTrackingResult = executionContext.getGraphQLContext().get(EXECUTION_TRACKING_KEY);
        executionTrackingResult.start(parameters.getPath(), System.nanoTime());
    }

    @Advice.OnMethodExit
    public static void invokeDataFetcherExit(@Advice.Argument(0) ExecutionContext executionContext,
                                             @Advice.Argument(1) ExecutionStrategyParameters parameters,
                                             @Advice.Return CompletableFuture<Object> result) {
        ExecutionTrackingResult executionTrackingResult = executionContext.getGraphQLContext().get(EXECUTION_TRACKING_KEY);
        ResultPath path = parameters.getPath();
        executionTrackingResult.end(path, System.nanoTime());
        if (result.isDone()) {
            if (result.isCancelled()) {
                executionTrackingResult.setDfResultTypes(path, ExecutionTrackingResult.DFResultType.DONE_CANCELLED);
            } else if (result.isCompletedExceptionally()) {
                executionTrackingResult.setDfResultTypes(path, ExecutionTrackingResult.DFResultType.DONE_EXCEPTIONALLY);
            } else {
                executionTrackingResult.setDfResultTypes(path, ExecutionTrackingResult.DFResultType.DONE_OK);
            }
        } else {
            executionTrackingResult.setDfResultTypes(path, ExecutionTrackingResult.DFResultType.PENDING);
        }
    }

}
