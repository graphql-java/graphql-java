/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

/**
 *
 * @author gkesler
 */
interface NodeVertexVisitor<U> {
    default U visit (OperationVertex node, U data) {
        return data;
    }
    
    default U visit (FieldVertex node, U data) {
        return data;
    }
}
