package graphql.schema.idl.errors;

import graphql.Internal;

@Internal
public class SchemaMissingError extends BaseError {

    public SchemaMissingError() {
        super(null, "There is no top level schema object defined");
    }
}
