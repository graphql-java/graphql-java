package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import org.jspecify.annotations.NonNull;

@Internal
public class InvalidUnicodeSyntaxException extends InvalidSyntaxException {

    public InvalidUnicodeSyntaxException(@NonNull I18n i18N, @NonNull String msgKey, @NonNull SourceLocation sourceLocation, @NonNull String offendingToken) {
        super(i18N.msg(msgKey, offendingToken, sourceLocation.getLine(), sourceLocation.getColumn()),
                sourceLocation, offendingToken, null, null);
    }
}
