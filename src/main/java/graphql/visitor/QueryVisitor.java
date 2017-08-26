package graphql.visitor;

import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;

public interface QueryVisitor {

    void visitField(Field field, GraphQLFieldDefinition fieldDefinition, VisitPath visitPath);

}
