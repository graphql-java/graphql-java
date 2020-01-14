grammar GraphqlCommon;

operationType : SUBSCRIPTION | MUTATION | QUERY;

description : stringValue;

enumValue : enumValueName ;


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

baseName: NAME | FRAGMENT | QUERY | MUTATION | SUBSCRIPTION | SCHEMA | SCALAR | TYPE | INTERFACE | IMPLEMENTS | ENUM | UNION | INPUT | EXTEND | DIRECTIVE;
fragmentName: baseName | BooleanValue | NullValue;
enumValueName: baseName | ON_KEYWORD;

name: baseName | BooleanValue | NullValue | ON_KEYWORD;

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
ON_KEYWORD: 'on';
NAME: [_A-Za-z][_0-9A-Za-z]*;


IntValue : Sign? IntegerPart;

FloatValue : Sign? IntegerPart ('.' Digit+)? ExponentPart?;

Sign : '-';

IntegerPart : '0' | NonZeroDigit | NonZeroDigit Digit+;

NonZeroDigit: '1'.. '9';

ExponentPart : ('e'|'E') ('+'|'-')? Digit+;

Digit : '0'..'9';


StringValue
 : '"' ( ~["\\\n\r\u2028\u2029] | EscapedChar )* '"'
 ;

TripleQuotedStringValue
 : '"""' TripleQuotedStringPart? '"""'
 ;


// Fragments never become a token of their own: they are only used inside other lexer rules
fragment TripleQuotedStringPart : ( EscapedTripleQuote | ExtendedSourceCharacter )+?;
fragment EscapedTripleQuote : '\\"""';

// this is currently not covered by the spec because we allow all unicode chars
// u0009 = \t Horizontal tab
// u000a = \n line feed
// u000d = \r carriage return
// u0020 = space
fragment ExtendedSourceCharacter :[\u0009\u000A\u000D\u0020-\u{10FFFF}];

fragment ExtendedSourceCharacterWithoutLineFeed :[\u0009\u0020-\u{10FFFF}];

// this is the spec definition
// fragment SourceCharacter :[\u0009\u000A\u000D\u0020-\uFFFF];


Comment: '#' ExtendedSourceCharacterWithoutLineFeed* -> channel(2);

fragment EscapedChar :   '\\' (["\\/bfnrt] | Unicode) ;
fragment Unicode : 'u' Hex Hex Hex Hex ;
fragment Hex : [0-9a-fA-F] ;

LF: [\n] -> channel(3);
CR: [\r] -> channel(3);
LineTerminator: [\u2028\u2029] -> channel(3);

Space : [\u0020] -> channel(3);
Tab : [\u0009] -> channel(3);
Comma : ',' -> channel(3);
UnicodeBOM : [\ufeff] -> channel(3);
