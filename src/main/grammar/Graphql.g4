grammar Graphql;

// Document

document : definition+;

definition:
operationDefinition |
fragmentDefinition
;

operationDefinition:
selectionSet |
operationType NAME variableDefinitions? directives? selectionSet;

operationType : 'query' | 'mutation';

variableDefinitions : '(' variableDefinition+ ')';

variableDefinition : variable ':' type defaultValue?;

variable : '$' NAME;

defaultValue : '=' value;

// Operations

selectionSet :  '{' selection+ '}';

selection :
field |
fragmentSpread |
inlineFragment;

field : alias? NAME arguments? directives? selectionSet?;

alias : NAME ':';

arguments : '(' argument+ ')';

argument : NAME ':' value;

// Fragments

fragmentSpread : '...' fragmentName directives?;

inlineFragment : '...' 'on' typeCondition directives? selectionSet;

fragmentDefinition : 'fragment' fragmentName 'on' typeCondition directives? selectionSet;

fragmentName :  NAME;

typeCondition : typeName;

// Value


value :
variable |
IntValue |
FloatValue |
StringValue |
BooleanValue |
enumValue |
arrayValue |
objectValue;



enumValue : NAME ;

// Array Value

arrayValue: '[' value* ']';

// Object Value

objectValue: '{' objectField* '}';
objectField : NAME ':' value;

// Directives

directives : directive+;

directive :'@' NAME | '@' NAME ':' value;

// Types

type : typeName | listType | nonNullType;

typeName : NAME;
listType : '[' type ']';
nonNullType: typeName '!' | listType '!';



// Token

BooleanValue: 'true' | 'false';

NAME: [_A-Za-z][_0-9A-Za-z]* ;

IntValue : Sign? IntegerPart;

FloatValue : Sign? IntegerPart '.' Digit+ ExponentPart?;

Sign : '-';

IntegerPart : '0' | NonZeroDigit | NonZeroDigit Digit+;

NonZeroDigit: '1'.. '9';

ExponentPart : 'e' Sign? Digit+;

Digit : '0'..'9';

StringValue: '\"' StringCharacter+ '\"';

StringCharacter: [a-zA-Z0-9];

WS : [ \t\r\n]+ -> skip ;
