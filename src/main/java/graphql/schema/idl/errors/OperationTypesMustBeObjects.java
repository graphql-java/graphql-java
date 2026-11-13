package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.OperationTypeDefinition;

import static java.lang.String.format;

@Internal
public class OperationTypesMustBeObjects extends BaseError {

    public OperationTypesMustBeObjects(OperationTypeDefinition op) {
        super(op, format("The operation type '%s' MUST have a object type as its definition %s",
                op.getName(), lineCol(op)));
    }
}
