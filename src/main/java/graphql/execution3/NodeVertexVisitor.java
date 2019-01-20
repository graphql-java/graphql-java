/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Node;
import graphql.schema.GraphQLType;

/**
 *
 * @author gkesler
 */
interface NodeVertexVisitor<U> {
    default U visit (OperationVertex node, U data) {
        return visitNode(node, data);
    }
    
    default U visit (FieldVertex node, U data) {
        return visitNode(node, data);
    }
    
    default U visit (DocumentVertex node, U data) {
        return visitNode(node, data);
    }
    
    default U visitNode (NodeVertex<? extends Node, ? extends GraphQLType> vertex, U data) {
        return data;
    }
}
