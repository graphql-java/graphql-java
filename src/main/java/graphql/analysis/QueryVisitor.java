package graphql.analysis;

import graphql.Internal;

@Internal
public interface QueryVisitor {

    void visitField(QueryVisitorEnvironment queryVisitorEnvironment);

}
