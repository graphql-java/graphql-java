package graphql.parser;

import graphql.PublicApi;
import graphql.language.SourceLocation;

@PublicApi
public class ParseCancelledException extends InvalidSyntaxException {

    public ParseCancelledException(String msg, SourceLocation sourceLocation, String offendingToken) {
        super(sourceLocation, msg, null, offendingToken, null);
    }
}
