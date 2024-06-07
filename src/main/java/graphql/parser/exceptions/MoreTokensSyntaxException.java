package graphql.parser.exceptions;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import org.jetbrains.annotations.NotNull;

@Internal
public class MoreTokensSyntaxException extends InvalidSyntaxException {

    @Internal
    public MoreTokensSyntaxException(@NotNull I18n i18N, @NotNull SourceLocation sourceLocation, @NotNull String offendingToken, @NotNull String sourcePreview) {
        super(i18N.msg("InvalidSyntaxMoreTokens.full", offendingToken, sourceLocation.getLine(), sourceLocation.getColumn()),
                sourceLocation, offendingToken, sourcePreview, null);
    }

    @Internal
    public MoreTokensSyntaxException(@NotNull I18n i18N, @NotNull SourceLocation sourceLocation) {
        super(i18N.msg("InvalidSyntaxMoreTokens.noMessage", sourceLocation.getLine(), sourceLocation.getColumn()),
                sourceLocation, null, null, null);
    }

}
