grammar Graphql;
import GraphqlSDL, GraphqlOperation, GraphqlCommon;

@header {
    package graphql.parser.antlr;
}

@lexer::members {
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







