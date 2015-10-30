grammar Graphql;

// Document

document : definition+;

definition:
operationDefinition |
fragmentDefinition
;

operationDefinition:
selectionSet |
operationType  NAME? variableDefinitions? directives? selectionSet;

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

argument : NAME ':' valueWithVariable;

// Fragments

fragmentSpread : '...' fragmentName directives?;

inlineFragment : '...' 'on' typeCondition directives? selectionSet;

fragmentDefinition : 'fragment' fragmentName 'on' typeCondition directives? selectionSet;

fragmentName :  NAME;

typeCondition : typeName;

// Value


value :
IntValue |
FloatValue |
StringValue |
BooleanValue |
enumValue |
arrayValue |
objectValue;

valueWithVariable :
variable |
IntValue |
FloatValue |
StringValue |
BooleanValue |
enumValue |
arrayValueWithVariable |
objectValueWithVariable;


enumValue : NAME ;

// Array Value

arrayValue: '[' value* ']';

arrayValueWithVariable: '[' valueWithVariable* ']';


// Object Value

objectValue: '{' objectField* '}';
objectValueWithVariable: '{' objectFieldWithVariable* '}';
objectField : NAME ':' value;
objectFieldWithVariable : NAME ':' valueWithVariable;

// Directives

directives : directive+;

directive :'@' NAME arguments?;

// Types

type : typeName | listType | nonNullType;

typeName : NAME;
listType : '[' type ']';
nonNullType: typeName '!' | listType '!';



// Token

BooleanValue: 'true' | 'false';

NAME: [_A-Za-z][_0-9A-Za-z]* ;

IntValue : Sign? IntegerPart;

FloatValue : Sign? IntegerPart ('.' Digit+)? ExponentPart?;

Sign : '-';

IntegerPart : '0' | NonZeroDigit | NonZeroDigit Digit+;

NonZeroDigit: '1'.. '9';

ExponentPart : ('e'|'E') Sign? Digit+;

Digit : '0'..'9';


StringValue: '"' (~(["\\\n\r\u2028\u2029])|EscapedChar)* '"';

fragment EscapedChar :   '\\' (["\\/bfnrt] | Unicode) ;
fragment Unicode : 'u' Hex Hex Hex Hex ;
fragment Hex : [0-9a-fA-F] ;

Ignored: (Whitspace|Comma|LineTerminator|Comment) -> skip;

fragment Comment: '#' ~[\n\r\u2028\u2029]*;

fragment LineTerminator: [\n\r\u2028\u2029];

fragment Whitspace : [\t\u000b\f\u0020\u00a0];
fragment Comma : ',';