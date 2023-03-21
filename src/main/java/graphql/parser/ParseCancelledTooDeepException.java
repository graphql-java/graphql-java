package graphql.parser;

import graphql.Internal;
import graphql.language.SourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public class ParseCancelledTooDeepException extends InvalidSyntaxException {

    @Internal
    public ParseCancelledTooDeepException(String msg, @Nullable SourceLocation sourceLocation, @Nullable String offendingToken, int maxTokens, @NotNull String tokenType) {
        super(sourceLocation, msg, null, offendingToken, null);
    }
}
