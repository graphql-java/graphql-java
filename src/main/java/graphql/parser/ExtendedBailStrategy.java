package graphql.parser;

import graphql.Internal;
import graphql.language.SourceLocation;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;

@Internal
public class ExtendedBailStrategy extends BailErrorStrategy {
    private final MultiSourceReader multiSourceReader;

    public ExtendedBailStrategy(MultiSourceReader multiSourceReader) {
        this.multiSourceReader = multiSourceReader;
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
        String sourcePreview = AntlrHelper.createPreview(multiSourceReader, token.getLine());
        return new InvalidSyntaxException(sourceLocation,
                "There are more tokens in the query that have not been consumed",
                sourcePreview, token.getText(), null);
    }


    private InvalidSyntaxException mkException(Parser recognizer, RecognitionException cause) {
        String sourcePreview = null;
        String offendingToken = null;
        SourceLocation sourceLocation = null;
        Token currentToken = recognizer.getCurrentToken();
        if (currentToken != null) {
            sourceLocation = AntlrHelper.createSourceLocation(multiSourceReader, currentToken);
            offendingToken = currentToken.getText();
            sourcePreview = AntlrHelper.createPreview(multiSourceReader, currentToken.getLine());
        }
        return new InvalidSyntaxException(sourceLocation, null, sourcePreview, offendingToken, cause);
    }

}
