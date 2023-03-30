package graphql.parser;

import graphql.Internal;

@Internal
public class ParseCancelledTooManyCharsException extends InvalidSyntaxException {

    @Internal
    public ParseCancelledTooManyCharsException(String msg, int maxCharacters) {
        super(null, msg, null, null, null);
    }
}
