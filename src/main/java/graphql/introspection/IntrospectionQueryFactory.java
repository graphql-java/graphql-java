package graphql.introspection;

import graphql.PublicApi;

@PublicApi
public class IntrospectionQueryFactory {

    private final boolean includeSpecifiedBy;

    public IntrospectionQueryFactory(boolean includeSpecifiedBy) {
        this.includeSpecifiedBy = includeSpecifiedBy;
    }


}
