/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.execution.ExecutionContext;
import graphql.execution2.ExecutionStrategy;
import graphql.execution2.FieldSubSelection;
import graphql.execution2.result.ObjectExecutionResultNode;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author gkesler
 */
public class TopOrderExecutionStrategy implements ExecutionStrategy {
    public TopOrderExecutionStrategy (ExecutionContext executionContext) {
        this.executionContext = Objects.requireNonNull(executionContext);
    }
    
    @Override
    public CompletableFuture<ObjectExecutionResultNode.RootExecutionResultNode> execute (FieldSubSelection fieldSubSelection) {
        // 1. build a dependency graph from the AST
        // 2. resolve fields in topological order provided by the dependency graph
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    final ExecutionContext executionContext;
}
