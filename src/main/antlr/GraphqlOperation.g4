grammar GraphqlOperation;
import GraphqlCommon;

operationDefinition:
selectionSet |
operationType  name? variableDefinitions? directives? selectionSet;

variableDefinitions : '(' variableDefinition+ ')';

variableDefinition : variable ':' type defaultValue? directives?;


selectionSet :  '{' selection+ '}';

selection :
field |
fragmentSpread |
inlineFragment;

field : alias? name arguments? directives? selectionSet?;

alias : name ':';



fragmentSpread : '...' fragmentName directives?;

inlineFragment : '...' typeCondition? directives? selectionSet;

fragmentDefinition : FRAGMENT fragmentName typeCondition directives? selectionSet;


typeCondition : ON_KEYWORD typeName;
