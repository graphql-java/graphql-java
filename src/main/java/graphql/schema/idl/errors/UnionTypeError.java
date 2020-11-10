package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.Node;

@Internal
public class UnionTypeError extends BaseError {
    public UnionTypeError(Node node, String msg) {
        super(node, msg);
    }
}
