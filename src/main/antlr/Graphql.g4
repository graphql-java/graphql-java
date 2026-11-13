grammar Graphql;
import GraphqlSDL, GraphqlOperation, GraphqlCommon;

@header {
    package graphql.parser.antlr;
}

@lexer::members {
    private int maxNumericLiteralCharacters = Integer.MAX_VALUE;

    public void setMaxNumericLiteralCharacters(int maxNumericLiteralCharacters) {
        this.maxNumericLiteralCharacters = maxNumericLiteralCharacters;
    }

    private boolean numericLiteralLengthWithinLimit() {
        int start = _tokenStartCharIndex;
        int end = _input.index();
        if (end - start > maxNumericLiteralCharacters) {
            throw new NumericLiteralTooLongException(_tokenStartLine, _tokenStartCharPositionInLine, maxNumericLiteralCharacters);
        }
        return true;
    }

    public boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }
    public boolean isNameStart(int c) {
        return '_' == c ||
          (c >= 'A' && c <= 'Z') ||
          (c >= 'a' && c <= 'z');
    }
    public boolean isDot(int c) {
        return '.' == c;
    }
}


document : definition+;

definition:
operationDefinition |
fragmentDefinition |
typeSystemDefinition |
typeSystemExtension
;







