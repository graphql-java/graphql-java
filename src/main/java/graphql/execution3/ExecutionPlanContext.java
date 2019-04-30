/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Node;
import graphql.schema.GraphQLType;
import graphql.util.DependencyGraphContext;
import graphql.util.Edge;

public interface ExecutionPlanContext extends DependencyGraphContext {
    /**
     * A callback method called when ExecutionPlan needs to prepare edge vertices for execution.
     * For instance, propagate &amp; transform results from source vertex to become sources in sink vertex
     * 
     * @param edge the edge being followed (source -- to --&gt; sink)
     */
    void prepareResolve (Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge);
    /**
     * A callback method called when ExecutionPlan has finished resolving the edge.
     * Resolution results are stored in the edge source vertex results
     * Edge sink is a vertex that may accumulate the overall result.
     * 
     * @param edge the edge being followed
     */
    void whenResolved (Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge);
    /**
     * A callback method called when ExecutionPlan attempts to auto resolve a vertex,
     * i.e. without invoking external data fetcher
     * 
     * @param node vertex to attempt to auto resolve
     * @return {@code true} if auto resolved, {@code false} otherwise
     */
    boolean resolve (NodeVertex<? extends Node, ? extends GraphQLType> node);
}
