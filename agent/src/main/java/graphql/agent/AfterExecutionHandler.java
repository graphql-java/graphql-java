package graphql.agent;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;

import java.util.function.BiConsumer;

public class AfterExecutionHandler implements BiConsumer<Object, Throwable> {

    private final ExecutionContext executionContext;

    public AfterExecutionHandler(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public void accept(Object o, Throwable throwable) {
        ExecutionId executionId = executionContext.getExecutionId();
        GraphQLJavaAgent.ExecutionData executionData = GraphQLJavaAgent.executionIdToData.get(executionId);
        executionData.endExecutionTime.set(System.nanoTime());
        System.out.println("execution finished for: " + executionId + " with data " + executionData);
        System.out.println(executionData.print(executionId.toString()));
    }


}
