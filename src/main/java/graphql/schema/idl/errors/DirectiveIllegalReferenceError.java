package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.DirectiveDefinition;
import graphql.language.NamedNode;

@Internal
public class DirectiveIllegalReferenceError extends BaseError {
    public DirectiveIllegalReferenceError(DirectiveDefinition directive, NamedNode location) {
        super(directive,
                String.format("'%s' must not reference itself on '%s''%s'",
                        directive.getName(), location.getName(), lineCol(location)
                ));
    }

    public DirectiveIllegalReferenceError(DirectiveDefinition directive, NamedNode location, String cyclePath) {
        super(directive,
                String.format("'%s' must not reference itself via directive cycle '%s' on '%s''%s'",
                        directive.getName(), cyclePath, location.getName(), lineCol(location)
                ));
    }
}
