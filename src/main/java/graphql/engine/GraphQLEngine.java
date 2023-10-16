package graphql.engine;

import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.instrumentation.Instrumentation;

import java.util.concurrent.CompletableFuture;

public interface GraphQLEngine {

    Instrumentation getInstrumentation();

    CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, EngineParameters parameters) throws NonNullableFieldWasNullException;
}
