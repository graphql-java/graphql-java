package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Internal
public class ParseCancelledTooDeepException extends InvalidSyntaxException {

    @Internal
    public ParseCancelledTooDeepException(@NonNull I18n i18N, @Nullable SourceLocation sourceLocation, @Nullable String offendingToken, int maxTokens, @NonNull String tokenType) {
        super(i18N.msg("ParseCancelled.tooDeep", maxTokens, tokenType),
                sourceLocation, offendingToken, null, null);
    }
}
