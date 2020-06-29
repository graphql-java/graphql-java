package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.SchemaDefinition;

import static java.lang.String.format;

@Internal
public class SchemaRedefinitionError extends BaseError {

    public SchemaRedefinitionError(SchemaDefinition oldEntry, SchemaDefinition newEntry) {
        super(oldEntry, format("There is already a schema defined %s.  The offending new one is here %s",
                lineCol(oldEntry), lineCol(newEntry)));
    }
}
