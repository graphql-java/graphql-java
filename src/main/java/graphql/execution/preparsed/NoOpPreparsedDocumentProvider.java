package graphql.execution.preparsed;


import graphql.ExecutionInput;
import graphql.Internal;
import graphql.execution.CF;
import graphql.execution.Execution;
import graphql.execution.ExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Internal
public class NoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {
    public static final NoOpPreparsedDocumentProvider INSTANCE = new NoOpPreparsedDocumentProvider();

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        ExecutionContext executionContext = executionInput.getGraphQLContext().get(Execution.EXECUTION_CONTEXT_KEY);
        return CF.completedFuture(parseAndValidateFunction.apply(executionInput), executionContext);
    }
}
