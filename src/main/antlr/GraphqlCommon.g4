grammar GraphqlCommon;

operationType : SUBSCRIPTION | MUTATION | QUERY;

description : stringValue;

enumValue : name ;


arrayValue: '[' value* ']';

arrayValueWithVariable: '[' valueWithVariable* ']';



objectValue: '{' objectField* '}';
objectValueWithVariable: '{' objectFieldWithVariable* '}';
objectField : name ':' value;
objectFieldWithVariable : name ':' valueWithVariable;


directives : directive+;

directive :'@' name arguments?;


arguments : '(' argument+ ')';

argument : name ':' valueWithVariable;

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


variable : '$' name;

defaultValue : '=' value;

stringValue
 : TripleQuotedStringValue
 | StringValue
 ;
type : typeName | listType | nonNullType;

typeName : name;
listType : '[' type ']';
nonNullType: typeName '!' | listType '!';


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

FloatValue : Sign? IntegerPart ('.' Digit+)? ExponentPart?;

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
