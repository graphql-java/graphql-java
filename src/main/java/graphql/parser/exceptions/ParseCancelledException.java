package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public class ParseCancelledException extends InvalidSyntaxException {

    @Internal
    public ParseCancelledException(@NotNull I18n i18N, @Nullable SourceLocation sourceLocation, @Nullable String offendingToken, int maxTokens, @NotNull String tokenType) {
        super(i18N.msg("ParseCancelled.full", maxTokens, tokenType),
                sourceLocation, offendingToken, null, null);
    }
}
