package graphql.visitor;

import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

public interface QueryVisitor {

    void visitField(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLCompositeType parent, VisitPath visitPath);

}
