grammar Graphql;
import GraphqlSDL, GraphqlOperation;

@header {
    package graphql.parser.antlr;
}


document : definition+;

definition:
operationDefinition |
fragmentDefinition |
typeSystemDefinition
;







