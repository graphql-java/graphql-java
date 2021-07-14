grammar GraphqlCommon;


operationType : SUBSCRIPTION | MUTATION | QUERY;

description : StringValue;

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

baseName: NAME | FRAGMENT | QUERY | MUTATION | SUBSCRIPTION | SCHEMA | SCALAR | TYPE | INTERFACE | IMPLEMENTS | ENUM | UNION | INPUT | EXTEND | DIRECTIVE | REPEATABLE;
fragmentName: baseName | BooleanValue | NullValue;
enumValueName: baseName | ON_KEYWORD;

name: baseName | BooleanValue | NullValue | ON_KEYWORD;

value :
StringValue |
IntValue |
FloatValue |
BooleanValue |
NullValue |
enumValue |
arrayValue |
objectValue;


valueWithVariable :
variable |
StringValue |
IntValue |
FloatValue |
BooleanValue |
NullValue |
enumValue |
arrayValueWithVariable |
objectValueWithVariable;


variable : '$' name;

defaultValue : '=' value;

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
REPEATABLE: 'repeatable';
NAME: [_A-Za-z][_0-9A-Za-z]*;



// Int Value
IntValue :  IntegerPart { !isDigit(_input.LA(1)) && !isDot(_input.LA(1)) && !isNameStart(_input.LA(1))  }?;
fragment IntegerPart : NegativeSign? '0' | NegativeSign? NonZeroDigit Digit*;
fragment NegativeSign : '-';
fragment NonZeroDigit: '1'..'9';

// Float Value
FloatValue : ((IntegerPart FractionalPart ExponentPart) { !isDigit(_input.LA(1)) && !isDot(_input.LA(1)) && !isNameStart(_input.LA(1))  }?) |
    ((IntegerPart FractionalPart ) { !isDigit(_input.LA(1)) && !isDot(_input.LA(1)) && !isNameStart(_input.LA(1))  }?) |
    ((IntegerPart ExponentPart) { !isDigit(_input.LA(1)) && !isDot(_input.LA(1)) && !isNameStart(_input.LA(1))  }?);
fragment FractionalPart: '.' Digit+;
fragment ExponentPart :  ExponentIndicator Sign? Digit+;
fragment ExponentIndicator: 'e' | 'E';
fragment Sign: '+'|'-';
fragment Digit : '0'..'9';

// StringValue
StringValue:
'""'  { _input.LA(1) != '"'}? |
'"' StringCharacter+ '"' |
'"""' BlockStringCharacter*? '"""';

fragment BlockStringCharacter:
'\\"""'|
SourceCharacter;

// this is SourceCharacter without
// \u000a New line
// \u000d Carriage return
// \u0022 '"'
// \u005c '\'
fragment StringCharacter:
([\u0000-\u0009] | [\u000b\u000c\u000e-\u0021] | [\u0023-\u005b] | [\u005d-\ud7ff] | [\ue000-\u{10ffff}]) |
'\\u' EscapedUnicode  |
'\\' EscapedCharacter;

fragment EscapedCharacter :  ["\\/bfnrt];
fragment EscapedUnicode : Hex Hex Hex Hex | '{' Hex+ '}';
fragment Hex : [0-9a-fA-F];

// this is the spec definition. Excludes surrogate leading and trailing values.
fragment SourceCharacter : [\u0000-\ud7ff] | [\ue000-\u{10ffff}];

// CommentChar
fragment SourceCharacterWithoutLineFeed : [\u0000-\u0009] | [\u000b\u000c\u000e-\ud7ff] | [\ue000-\u{10ffff}];

Comment: '#' SourceCharacterWithoutLineFeed* -> channel(2);

LF: [\n] -> channel(3);
CR: [\r] -> channel(3);
LineTerminator: [\u2028\u2029] -> channel(3);

Space : [\u0020] -> channel(3);
Tab : [\u0009] -> channel(3);
Comma : ',' -> channel(3);
UnicodeBOM : [\ufeff] -> channel(3);
