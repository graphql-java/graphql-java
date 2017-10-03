grammar Graphql;

@header {
    package graphql.parser.antlr;
}

// Document 

document : definition+;

definition:
operationDefinition |
fragmentDefinition |
typeSystemDefinition
;

operationDefinition:
selectionSet |
operationType  name? variableDefinitions? directives? selectionSet;

operationType : SUBSCRIPTION | MUTATION | QUERY;

variableDefinitions : '(' variableDefinition+ ')';

variableDefinition : variable ':' type defaultValue?;

variable : '$' name;

defaultValue : '=' value;

// Operations

selectionSet :  '{' selection+ '}';

selection :
field |
fragmentSpread |
inlineFragment;

field : alias? name arguments? directives? selectionSet?;

alias : name ':';

arguments : '(' argument+ ')';

argument : name ':' valueWithVariable;

// Fragments

fragmentSpread : '...' fragmentName directives?;

inlineFragment : '...' typeCondition? directives? selectionSet;

fragmentDefinition : 'fragment' fragmentName typeCondition directives? selectionSet;

fragmentName :  name;

typeCondition : 'on' typeName;

// Value

name: NAME | FRAGMENT | QUERY | MUTATION | SUBSCRIPTION | SCHEMA | SCALAR | TYPE | INTERFACE | IMPLEMENTS | ENUM | UNION | INPUT | EXTEND | DIRECTIVE;

value :
stringValue |
IntValue |
FloatValue |
BooleanValue |
NullValue |
enumValue |
arrayValue |
objectValue;


valueWithVariable :
variable |
stringValue |
IntValue |
FloatValue |
BooleanValue |
NullValue |
enumValue |
arrayValueWithVariable |
objectValueWithVariable;


stringValue
 : TripleQuotedStringValue
 | StringValue
 ;

description : stringValue;

enumValue : name ;

// Array Value

arrayValue: '[' value* ']';

arrayValueWithVariable: '[' valueWithVariable* ']';


// Object Value

objectValue: '{' objectField* '}';
objectValueWithVariable: '{' objectFieldWithVariable* '}';
objectField : name ':' value;
objectFieldWithVariable : name ':' valueWithVariable;

// Directives

directives : directive+;

directive :'@' name arguments?;

// Types

type : typeName | listType | nonNullType;

typeName : name;
listType : '[' type ']';
nonNullType: typeName '!' | listType '!';


// Type System
typeSystemDefinition: description?
schemaDefinition |
typeDefinition |
typeExtensionDefinition |
directiveDefinition
;

schemaDefinition : description? SCHEMA directives? '{' operationTypeDefinition+ '}';

operationTypeDefinition : description? operationType ':' typeName;

typeDefinition:
scalarTypeDefinition |
objectTypeDefinition |
interfaceTypeDefinition |
unionTypeDefinition |
enumTypeDefinition |
inputObjectTypeDefinition
;

scalarTypeDefinition : description? SCALAR name directives?;

objectTypeDefinition : description? TYPE name implementsInterfaces? directives? '{' fieldDefinition+ '}';

implementsInterfaces : IMPLEMENTS typeName+;

fieldDefinition : description? name argumentsDefinition? ':' type directives?;

argumentsDefinition : '(' inputValueDefinition+ ')';

inputValueDefinition : description? name ':' type defaultValue? directives?;

interfaceTypeDefinition : description? INTERFACE name directives? '{' fieldDefinition+ '}';

unionTypeDefinition : description? UNION name directives? '=' unionMembers;

unionMembers:
typeName |
unionMembers '|' typeName
;

enumTypeDefinition : description? ENUM name directives? '{' enumValueDefinition+ '}';

enumValueDefinition : description? enumValue directives?;

inputObjectTypeDefinition : description? INPUT name directives? '{' inputValueDefinition+ '}';

//
// type extensions dont get "description" strings according to reference implementation
// https://github.com/graphql/graphql-js/pull/927/files#diff-b9370666fe8cd9ff4dd53e89e60d26afR182
//
typeExtensionDefinition : EXTEND objectTypeDefinition;

directiveDefinition : description? DIRECTIVE '@' name argumentsDefinition? 'on' directiveLocations;

directiveLocation : name;

directiveLocations :
directiveLocation |
directiveLocations '|' directiveLocation
;


// Token

BooleanValue: 'true' | 'false';

NullValue: 'null';

FRAGMENT: 'fragment';
QUERY: 'query';
MUTATION: 'mutation';
SUBSCRIPTION: 'subscription';
SCHEMA: 'schema';
SCALAR: 'scalar';
TYPE: 'type';
INTERFACE: 'interface';
IMPLEMENTS: 'implements';
ENUM: 'enum';
UNION: 'union';
INPUT: 'input';
EXTEND: 'extend';
DIRECTIVE: 'directive';
NAME: [_A-Za-z][_0-9A-Za-z]*;


IntValue : Sign? IntegerPart;

FloatValue : Sign? IntegerPart ('.' Digit*)? ExponentPart?;

Sign : '-';

IntegerPart : '0' | NonZeroDigit | NonZeroDigit Digit+;

NonZeroDigit: '1'.. '9';

ExponentPart : ('e'|'E') Sign? Digit+;

Digit : '0'..'9';


StringValue
 : '"' ( ~["\\\n\r\u2028\u2029] | EscapedChar )* '"'
 ;

TripleQuotedStringValue
 : '"""' TripleQuotedStringPart? '"""'
 ;


// Fragments never become a token of their own: they are only used inside other lexer rules
fragment TripleQuotedStringPart : ( EscapedTripleQuote | SourceCharacter )+?;
fragment EscapedTripleQuote : '\\"""';
fragment SourceCharacter :[\u0009\u000A\u000D\u0020-\uFFFF];

Comment: '#' ~[\n\r\u2028\u2029]* -> channel(2);

Ignored: (UnicodeBOM|Whitespace|LineTerminator|Comma) -> skip;

fragment EscapedChar :   '\\' (["\\/bfnrt] | Unicode) ;
fragment Unicode : 'u' Hex Hex Hex Hex ;
fragment Hex : [0-9a-fA-F] ;

fragment LineTerminator: [\n\r\u2028\u2029];

fragment Whitespace : [\u0009\u0020];
fragment Comma : ',';
fragment UnicodeBOM : [\ufeff];
