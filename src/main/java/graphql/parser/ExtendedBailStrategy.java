package graphql.parser;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.language.SourceLocation;
import graphql.parser.exceptions.MoreTokensSyntaxException;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.List;

@Internal
public class ExtendedBailStrategy extends BailErrorStrategy {
    private final MultiSourceReader multiSourceReader;
    private final ParserEnvironment environment;

    public ExtendedBailStrategy(MultiSourceReader multiSourceReader, ParserEnvironment environment) {
        this.multiSourceReader = multiSourceReader;
        this.environment = environment;
    }

    @Override
    public void recover(Parser recognizer, RecognitionException e) {
        try {
            super.recover(recognizer, e);
        } catch (ParseCancellationException parseException) {
            throw mkException(recognizer, e);
        }
    }

    @Override
    public Token recoverInline(Parser recognizer) throws RecognitionException {
        try {
            return super.recoverInline(recognizer);
        } catch (ParseCancellationException parseException) {
            throw mkException(recognizer, null);
        }
    }

    InvalidSyntaxException mkMoreTokensException(Token token) {
        SourceLocation sourceLocation = AntlrHelper.createSourceLocation(multiSourceReader, token);
        if (environment.getParserOptions().isRedactTokenParserErrorMessages()) {
            return new MoreTokensSyntaxException(environment.getI18N(), sourceLocation);
        }

        String sourcePreview = AntlrHelper.createPreview(multiSourceReader, token.getLine());
        return new MoreTokensSyntaxException(environment.getI18N(), sourceLocation,
                token.getText(), sourcePreview);
    }


    private InvalidSyntaxException mkException(Parser recognizer, RecognitionException cause) {
        String sourcePreview;
        String offendingToken;
        final SourceLocation sourceLocation;
        Token currentToken = recognizer.getCurrentToken();
        if (currentToken != null) {
            sourceLocation = AntlrHelper.createSourceLocation(multiSourceReader, currentToken);
            offendingToken = currentToken.getText();
            sourcePreview = AntlrHelper.createPreview(multiSourceReader, currentToken.getLine());
        } else {
            sourcePreview = null;
            offendingToken = null;
            sourceLocation = null;
        }

        String msgKey;
        List<Object> args;
        SourceLocation location = sourceLocation == null ? SourceLocation.EMPTY : sourceLocation;
        if (offendingToken == null || environment.getParserOptions().isRedactTokenParserErrorMessages()) {
            msgKey = "InvalidSyntaxBail.noToken";
            args = ImmutableList.of(location.getLine(), location.getColumn());
        } else {
            msgKey = "InvalidSyntaxBail.full";
            args = ImmutableList.of(offendingToken, sourceLocation.getLine(), sourceLocation.getColumn());
        }
        String msg = environment.getI18N().msg(msgKey, args);
        return new InvalidSyntaxException(msg, sourceLocation, offendingToken, sourcePreview, cause);
    }

}
