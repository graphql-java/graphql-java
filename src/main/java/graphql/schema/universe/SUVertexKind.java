package graphql.schema.universe;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * The kinds of vertices stored in a {@link SchemaUniverse}.
 */
@ExperimentalApi
@NullMarked
public enum SUVertexKind {
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
    LIST,
    NON_NULL
}
