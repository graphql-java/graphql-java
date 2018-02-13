grammar GraphqlSDL;
import GraphqlCommon;

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

objectTypeDefinition : description? TYPE name implementsInterfaces? directives? fieldsDefinition?;

implementsInterfaces :
    IMPLEMENTS '&'? typeName+ |
    implementsInterfaces '&' typeName ;

fieldsDefinition : '{' fieldDefinition+ '}';

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
