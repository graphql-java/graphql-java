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

/**
 *
 * @author gkesler
 */
public interface ExecutionPlanContext extends DependencyGraphContext {
    void prepareResolve (Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge);
    void whenResolved (Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge);
    boolean resolve (NodeVertex<? extends Node, ? extends GraphQLType> node);
}
