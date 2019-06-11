grammar Graphql;
import GraphqlSDL, GraphqlOperation, GraphqlCommon;

@header {
    package graphql.parser.antlr;
}


document : definition+;

definition:
operationDefinition |
fragmentDefinition |
typeSystemDefinition
;







