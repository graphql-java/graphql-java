package graphql.schema;

import graphql.PublicApi;

/**
 * The type of a schema coordinate as defined in the GraphQL specification.
 *
 * @see SchemaCoordinate
 */
@PublicApi
public enum SchemaCoordinateType {
    /**
     * A named type coordinate, e.g. {@code User}
     */
    NAMED_TYPE,
    /**
     * A field coordinate on an object or interface type, e.g. {@code User.name}
     */
    FIELD,
    /**
     * An input field coordinate on an input object type, e.g. {@code SearchInput.query}
     */
    INPUT_FIELD,
    /**
     * An enum value coordinate, e.g. {@code Status.ACTIVE}
     */
    ENUM_VALUE,
    /**
     * A field argument coordinate, e.g. {@code Query.searchBusiness(criteria:)}
     */
    FIELD_ARGUMENT,
    /**
     * A directive coordinate, e.g. {@code @deprecated}
     */
    DIRECTIVE,
    /**
     * A directive argument coordinate, e.g. {@code @deprecated(reason:)}
     */
    DIRECTIVE_ARGUMENT
}
