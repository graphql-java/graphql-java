package graphql.schema.idl;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

@Internal
@NullMarked
public enum SchemaPrintChildKind {
    TOP_LEVEL,
    FIELD,
    ARGUMENT,
    INTERFACE,
    UNION_MEMBER,
    ENUM_VALUE,
    INPUT_FIELD,
    APPLIED_DIRECTIVE,
    APPLIED_DIRECTIVE_ARGUMENT
}
