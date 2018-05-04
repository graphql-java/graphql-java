package graphql.analysis;

import graphql.PublicApi;

@PublicApi
public interface QueryVisitor {

    void visitField(QueryVisitorEnvironment queryVisitorEnvironment);

}
