grammar GraphqlSDL;
import GraphqlCommon;

typeSystemDefinition: description?
schemaDefinition |
typeDefinition |
typeExtension |
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

//
// type extensions dont get "description" strings according to spec
// https://github.com/facebook/graphql/blob/master/spec/Appendix%20B%20--%20Grammar%20Summary.md
//

typeExtension :
    objectTypeExtensionDefinition |
    interfaceTypeExtensionDefinition |
    unionTypeExtensionDefinition |
    scalarTypeExtensionDefinition |
    enumTypeExtensionDefinition |
    inputObjectTypeExtensionDefinition
;

emptyParentheses : '{' '}';

scalarTypeDefinition : description? SCALAR name directives?;

scalarTypeExtensionDefinition : EXTEND SCALAR name directives;

objectTypeDefinition : description? TYPE name implementsInterfaces? directives? fieldsDefinition?;

objectTypeExtensionDefinition :
    EXTEND TYPE name implementsInterfaces? directives? extensionFieldsDefinition |
    EXTEND TYPE name implementsInterfaces? directives emptyParentheses? |
    EXTEND TYPE name implementsInterfaces
;

implementsInterfaces :
    IMPLEMENTS '&'? typeName+ |
    implementsInterfaces '&' typeName ;

fieldsDefinition : '{' fieldDefinition* '}';

extensionFieldsDefinition : '{' fieldDefinition+ '}';

fieldDefinition : description? name argumentsDefinition? ':' type directives?;

argumentsDefinition : '(' inputValueDefinition+ ')';

inputValueDefinition : description? name ':' type defaultValue? directives?;

interfaceTypeDefinition : description? INTERFACE name directives? fieldsDefinition?;

interfaceTypeExtensionDefinition :
    EXTEND INTERFACE name directives? extensionFieldsDefinition |
    EXTEND INTERFACE name directives emptyParentheses?
;


unionTypeDefinition : description? UNION name directives? unionMembership?;

unionTypeExtensionDefinition :
    EXTEND UNION name directives? unionMembership |
    EXTEND UNION name directives
;

unionMembership : '=' unionMembers;

unionMembers:
'|'? typeName |
unionMembers '|' typeName
;

enumTypeDefinition : description? ENUM name directives? enumValueDefinitions?;

enumTypeExtensionDefinition :
    EXTEND ENUM name directives? extensionEnumValueDefinitions |
    EXTEND ENUM name directives emptyParentheses?
;

enumValueDefinitions : '{' enumValueDefinition* '}';

extensionEnumValueDefinitions : '{' enumValueDefinition+ '}';

enumValueDefinition : description? enumValue directives?;


inputObjectTypeDefinition : description? INPUT name directives? inputObjectValueDefinitions?;

inputObjectTypeExtensionDefinition :
    EXTEND INPUT name directives? extensionInputObjectValueDefinitions |
    EXTEND INPUT name directives emptyParentheses?
;

inputObjectValueDefinitions : '{' inputValueDefinition* '}';

extensionInputObjectValueDefinitions : '{' inputValueDefinition+ '}';


directiveDefinition : description? DIRECTIVE '@' name argumentsDefinition? 'on' directiveLocations;

directiveLocation : name;

directiveLocations :
directiveLocation |
directiveLocations '|' directiveLocation
;
