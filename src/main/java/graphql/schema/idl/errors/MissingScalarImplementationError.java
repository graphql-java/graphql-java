package graphql.schema.idl.errors;

import graphql.Internal;

import static java.lang.String.format;

@Internal
public class MissingScalarImplementationError extends BaseError {

    public MissingScalarImplementationError(String scalarName) {
        super(null, format("There is no scalar implementation for the named  '%s' scalar type", scalarName));
    }

}
