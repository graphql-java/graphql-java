/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Document;
import graphql.schema.GraphQLType;
import graphql.util.DependencyGraphContext;
import java.util.Objects;

/**
 *
 * @author gkesler
 */
public class DocumentVertex extends NodeVertex<Document, GraphQLType> {
    public DocumentVertex(Document node) {
        super(Objects.requireNonNull(node), null);
    }

    @Override
    public boolean canResolve(DependencyGraphContext context) {
        return true;
    }

    @Override
    <U> U accept(U data, NodeVertexVisitor<? super U> visitor) {
        return (U)visitor.visit(this, data);
    }    
}
