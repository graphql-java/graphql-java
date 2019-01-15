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
    CompletableFuture<ExecutionResult> execute (ExecutionPlan executionPlan);
}
