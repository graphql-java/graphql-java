grammar Graphql;
import GraphqlSDL, GraphqlOperation, GraphqlCommon;

@header {
    package graphql.parser.antlr;
}

@lexer::members {
    private int maxNumericDigits = Integer.MAX_VALUE;

    public void setMaxNumericDigits(int maxNumericDigits) {
        this.maxNumericDigits = maxNumericDigits;
    }

    private boolean numericDigitCountWithinLimit() {
        int start = _tokenStartCharIndex;
        int end = _input.index();
        // A prefix this short cannot contain more than the permitted number of digits.
        if (end - start <= maxNumericDigits) {
            return true;
        }
        String matchedPrefix = _input.getText(org.antlr.v4.runtime.misc.Interval.of(start, end - 1));
        int digits = 0;
        for (int i = 0; i < matchedPrefix.length(); i++) {
            if (isDigit(matchedPrefix.charAt(i)) && ++digits > maxNumericDigits) {
                throw new NumericLiteralTooLongException(_tokenStartLine, _tokenStartCharPositionInLine, start, end - 1, maxNumericDigits);
            }
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







