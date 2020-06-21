package graphql.schema.idl.errors;

import graphql.language.Node;

public class UnionTypeError extends BaseError {
    public UnionTypeError(Node node, String msg) {
        super(node, msg);
    }
}
