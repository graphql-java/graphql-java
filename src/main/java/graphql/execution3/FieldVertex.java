/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Field;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import java.util.Objects;

/**
 *
 * @author gkesler
 */
public class FieldVertex extends NodeVertex<Field, GraphQLOutputType> {    
    public FieldVertex(Field node, GraphQLOutputType type, GraphQLFieldsContainer definedIn) {
        super(node, type);
        
        this.definedIn = Objects.requireNonNull(definedIn);
    }    

    public GraphQLFieldsContainer getDefinedIn() {
        return definedIn;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.node.getName());
        hash = 97 * hash + Objects.hashCode(this.node.getAlias());
        hash = 97 * hash + Objects.hashCode(this.type);
        hash = 97 * hash + Objects.hashCode(this.definedIn);
        return hash;
    }

    private final GraphQLFieldsContainer definedIn;
}
