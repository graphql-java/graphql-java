package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.DirectiveDefinition;
import graphql.language.NamedNode;

import java.util.List;

@Internal
public class DirectiveIllegalReferenceError extends BaseError {
    public DirectiveIllegalReferenceError(DirectiveDefinition directive, NamedNode location) {
        super(directive,
                String.format("'%s' must not reference itself on '%s''%s'",
                        directive.getName(), location.getName(), lineCol(location)
                ));
    }

    public DirectiveIllegalReferenceError(DirectiveDefinition directive, List<String> cyclePath) {
        super(directive,
                String.format("'%s' forms a directive cycle via: %s '%s'",
                        directive.getName(), String.join(" -> ", cyclePath), lineCol(directive)
                ));
    }
}