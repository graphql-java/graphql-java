package graphql.schema.idl;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

@Internal
@NullMarked
public enum SchemaPrintElementKind {
    SCHEMA,
    OBJECT,
    FIELD,
    INTERFACE,
    UNION,
    ENUM,
    ENUM_VALUE,
    SCALAR,
    INPUT_OBJECT,
    INPUT_FIELD,
    ARGUMENT,
    DIRECTIVE,
    APPLIED_DIRECTIVE,
    APPLIED_DIRECTIVE_ARGUMENT,
    LIST,
    NON_NULL
}
