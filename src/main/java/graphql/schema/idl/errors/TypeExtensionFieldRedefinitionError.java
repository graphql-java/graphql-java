package graphql.schema.idl.errors;

import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class TypeExtensionFieldRedefinitionError extends BaseError {

    public TypeExtensionFieldRedefinitionError(TypeDefinition typeDefinition, FieldDefinition fieldDefinition) {
        super(typeDefinition,
                format("'%s' extension type %s tried to redefine field '%s' %s",
                        typeDefinition.getName(), BaseError.lineCol(typeDefinition), fieldDefinition.getName(), BaseError.lineCol(fieldDefinition)
                ));
    }
}
