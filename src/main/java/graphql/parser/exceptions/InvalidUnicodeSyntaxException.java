package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import org.jetbrains.annotations.NotNull;

@Internal
public class InvalidUnicodeSyntaxException extends InvalidSyntaxException {

    public InvalidUnicodeSyntaxException(@NotNull I18n i18N, @NotNull String msgKey, @NotNull SourceLocation sourceLocation, @NotNull String offendingToken) {
        super(() -> i18N.msg(msgKey, offendingToken, sourceLocation.getLine(), sourceLocation.getColumn()),
                sourceLocation, offendingToken, null, null);
    }
}
