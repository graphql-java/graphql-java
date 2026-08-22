package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import org.jspecify.annotations.NonNull;

@Internal
public class ParseCancelledTooManyNumericLiteralCharactersException extends InvalidSyntaxException {

    @Internal
    public ParseCancelledTooManyNumericLiteralCharactersException(@NonNull I18n i18N, @NonNull SourceLocation sourceLocation, int maxCharacters) {
        super(i18N.msg("ParseCancelled.tooManyNumericLiteralCharacters", maxCharacters),
                sourceLocation, null, null, null);
    }
}
