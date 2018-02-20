package graphql.schema.idl.errors;

import graphql.language.ObjectTypeExtensionDefinition;

import static java.lang.String.format;

public class TypeExtensionMissingBaseTypeError extends BaseError {

    public TypeExtensionMissingBaseTypeError(ObjectTypeExtensionDefinition objectTypeExtensionDefinition) {
        super(objectTypeExtensionDefinition,
                format("The extension '%s' type %s is missing its base object type",
                        objectTypeExtensionDefinition.getName(), BaseError.lineCol(objectTypeExtensionDefinition)
                ));
    }
}
