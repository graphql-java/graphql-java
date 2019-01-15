/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import java.util.Objects;

/**
 *
 * @author gkesler
 */
public class OperationVertex extends NodeVertex<OperationDefinition, GraphQLObjectType> {    
    public OperationVertex(OperationDefinition node, GraphQLObjectType type) {
        super(Objects.requireNonNull(node), Objects.requireNonNull(type));
    }  
//
//    @Override
//    public boolean canResolve() {
//        return true;
//    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.node.getName());
        hash = 97 * hash + Objects.hashCode(this.node.getOperation());
        hash = 97 * hash + Objects.hashCode(this.type);
        return hash;
    }    

    @Override
    <U> U accept(U data, NodeVertexVisitor<? super U> visitor) {
        return (U)visitor.visit(this, data);
    }
}
