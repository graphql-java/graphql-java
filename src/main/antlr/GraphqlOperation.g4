grammar GraphqlOperation;
import GraphqlCommon;

operationDefinition:
selectionSet |
operationType  name? variableDefinitions? directives? selectionSet;

variableDefinitions : '(' variableDefinition+ ')';

variableDefinition : variable ':' type defaultValue?;


selectionSet :  '{' selection+ '}';

selection :
field |
fragmentSpread |
inlineFragment;

field : alias? name arguments? directives? selectionSet?;

alias : name ':';




fragmentSpread : '...' fragmentName directives?;

inlineFragment : '...' typeCondition? directives? selectionSet;

fragmentDefinition : 'fragment' fragmentName typeCondition directives? selectionSet;

fragmentName :  name;

typeCondition : 'on' typeName;
