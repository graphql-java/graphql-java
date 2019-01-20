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
import java.util.function.BiConsumer;

/**
 *
 * @author gkesler
 */
public class NodeEdge extends Edge<NodeVertex<Node, GraphQLType>, NodeEdge> {
    public <N extends NodeVertex<? extends Node, ? extends GraphQLType>> NodeEdge(N source, N sink) {
        super((NodeVertex<Node, GraphQLType>)source, (NodeVertex<Node, GraphQLType>)sink);
    }

    public <N extends NodeVertex<? extends Node, ? extends GraphQLType>> NodeEdge(N source, N sink, BiConsumer<? super DependencyGraphContext, ? super NodeEdge> action) {
        super((NodeVertex<Node, GraphQLType>)source, (NodeVertex<Node, GraphQLType>)sink, action);
    }    
}
