package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.parser.InvalidSyntaxException;
import org.jspecify.annotations.NonNull;

@Internal
public class ParseCancelledTooManyCharsException extends InvalidSyntaxException {

    @Internal
    public ParseCancelledTooManyCharsException(@NonNull I18n i18N, int maxCharacters) {
        super(i18N.msg("ParseCancelled.tooManyChars", maxCharacters),
                null, null, null, null);
    }
}
