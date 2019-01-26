/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.ExecutionResult;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author gkesler
 */
public interface ExecutionStrategy {
    /**
     * Executes a graphql request according to the schedule
     * provided by executionPlan
     * 
     * @param executionPlan a {@code graphql.util.DependencyGraph} specialization that provides
     * order of field resolution requets
     * 
     * @return a CompletableFuture holding the result of execution.
     */
    CompletableFuture<ExecutionResult> execute (ExecutionPlan executionPlan);
}
