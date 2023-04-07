package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.parser.InvalidSyntaxException;
import org.jetbrains.annotations.NotNull;

@Internal
public class ParseCancelledTooManyCharsException extends InvalidSyntaxException {

    @Internal
    public ParseCancelledTooManyCharsException(@NotNull I18n i18N, int maxCharacters) {
        super(i18N.msg("ParseCancelled.tooManyChars", maxCharacters),
                null, null, null, null);
    }
}
