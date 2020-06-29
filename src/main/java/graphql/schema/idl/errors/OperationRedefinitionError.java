package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.OperationTypeDefinition;

import static java.lang.String.format;

@Internal
public class OperationRedefinitionError extends BaseError {

    public OperationRedefinitionError(OperationTypeDefinition oldEntry, OperationTypeDefinition newEntry) {
        super(oldEntry, format("There is already an operation '%s' defined %s.  The offending new one is here %s",
                oldEntry.getName(), lineCol(oldEntry), lineCol(newEntry)));
    }
}
