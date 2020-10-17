package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.NamedNode;

@Internal
public class IllegalNameError extends BaseError {
    public IllegalNameError(NamedNode directiveDefinition) {
        super(directiveDefinition,
                String.format("'%s''%s' must not begin with '__', which is reserved by GraphQL introspection.",
                        directiveDefinition.getName(), lineCol(directiveDefinition)
                ));
    }
}