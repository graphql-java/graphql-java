package graphql.schema.idl.errors;

import graphql.language.TypeExtensionDefinition;

import static java.lang.String.format;

public class TypeExtensionMissingBaseTypeError extends BaseError {

    public TypeExtensionMissingBaseTypeError(TypeExtensionDefinition typeExtensionDefinition) {
        super(typeExtensionDefinition,
                format("The extension '%s' type %s is missing its base object type",
                        typeExtensionDefinition.getName(), BaseError.lineCol(typeExtensionDefinition)
                ));
    }
}
