package graphql.execution;

public class ShadedJarConsumer {

    public Object getOperationDirectives(ExecutionContext executionContext) {
        var operationDirectives = executionContext.getOperationDirectives();
        return operationDirectives;
    }
}
