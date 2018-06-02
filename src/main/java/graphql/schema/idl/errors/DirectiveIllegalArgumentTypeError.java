package graphql.schema.idl.errors;

import graphql.language.Node;

import static java.lang.String.format;

public class DirectiveIllegalArgumentTypeError extends BaseError {

    public DirectiveIllegalArgumentTypeError(Node element, String elementName, String directiveName, String argumentName) {
        super(element,
                format("'%s' %s use an illegal value for the argument '%s' on directive '%s'",
                        elementName, BaseError.lineCol(element), argumentName, directiveName
                ));
    }
}
