package graphql.analysis;

import graphql.Internal;

@Internal
public interface FieldVisitor {

    void visitField(QueryVisitorEnvironment queryVisitorEnvironment);

}
