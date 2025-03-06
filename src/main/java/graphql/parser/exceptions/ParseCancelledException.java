package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Internal
public class ParseCancelledException extends InvalidSyntaxException {

    @Internal
    public ParseCancelledException(@NonNull I18n i18N, @Nullable SourceLocation sourceLocation, @Nullable String offendingToken, int maxTokens, @NonNull String tokenType) {
        super(i18N.msg("ParseCancelled.full", maxTokens, tokenType),
                sourceLocation, offendingToken, null, null);
    }
}
