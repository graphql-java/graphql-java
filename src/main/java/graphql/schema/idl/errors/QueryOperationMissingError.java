package graphql.schema.idl.errors;

import graphql.Internal;

@Internal
public class QueryOperationMissingError extends BaseError {

    public QueryOperationMissingError() {
        super(null, "A schema MUST have a 'query' operation defined");
    }
}
