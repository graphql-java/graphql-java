package graphql.schema;

import graphql.AssertException;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

/**
 * Represents a Schema Coordinate as defined in the GraphQL specification.
 * <p>
 * Schema coordinates uniquely identify a named element within a GraphQL schema.
 * The following coordinate types are supported:
 * <ul>
 *   <li>Named type: {@code User}</li>
 *   <li>Field: {@code User.name}</li>
 *   <li>Input field: {@code SearchInput.query}</li>
 *   <li>Enum value: {@code Status.ACTIVE}</li>
 *   <li>Field argument: {@code Query.searchBusiness(criteria:)}</li>
 *   <li>Directive: {@code @deprecated}</li>
 *   <li>Directive argument: {@code @deprecated(reason:)}</li>
 * </ul>
 * <p>
 * Note: field coordinates, input field coordinates, and enum value coordinates all share
 * the same string representation ({@code TypeName.memberName}). The {@link SchemaCoordinateType}
 * disambiguates them when needed. The {@link #parse(String)} method produces {@code FIELD}
 * for the {@code TypeName.memberName} form since the member kind cannot be determined from
 * the string alone.
 *
 * @see SchemaCoordinateType
 * @see FieldCoordinates
 */
@PublicApi
@NullMarked
public class SchemaCoordinate {

    private final SchemaCoordinateType type;
    private final @Nullable String typeName;
    private final @Nullable String memberName;
    private final @Nullable String argumentName;

    private SchemaCoordinate(SchemaCoordinateType type, @Nullable String typeName,
                             @Nullable String memberName, @Nullable String argumentName) {
        this.type = assertNotNull(type, "type is required");
        this.typeName = typeName;
        this.memberName = memberName;
        this.argumentName = argumentName;
    }

    /**
     * @return the type of this schema coordinate
     */
    public SchemaCoordinateType getType() {
        return type;
    }

    /**
     * @return the type name for type-based coordinates (NAMED_TYPE, FIELD, INPUT_FIELD, ENUM_VALUE, FIELD_ARGUMENT),
     * or null for directive-based coordinates
     */
    public @Nullable String getTypeName() {
        return typeName;
    }

    /**
     * @return the field name for FIELD and FIELD_ARGUMENT coordinates, the input field name for INPUT_FIELD,
     * the enum value name for ENUM_VALUE, or null for other coordinate types
     */
    public @Nullable String getMemberName() {
        return memberName;
    }

    /**
     * @return the argument name for FIELD_ARGUMENT and DIRECTIVE_ARGUMENT coordinates, or null for other types
     */
    public @Nullable String getArgumentName() {
        return argumentName;
    }

    /**
     * For DIRECTIVE and DIRECTIVE_ARGUMENT coordinates, returns the directive name (without the {@code @} prefix).
     * This is an alias for {@link #getTypeName()} that is clearer for directive coordinates.
     *
     * @return the directive name, or null for non-directive coordinates
     */
    public @Nullable String getDirectiveName() {
        return typeName;
    }

    /**
     * Produces the spec-standard string representation of this schema coordinate.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code User} - named type</li>
     *   <li>{@code User.name} - field / input field / enum value</li>
     *   <li>{@code Query.search(term:)} - field argument</li>
     *   <li>{@code @deprecated} - directive</li>
     *   <li>{@code @deprecated(reason:)} - directive argument</li>
     * </ul>
     */
    @Override
    public String toString() {
        return formatCoordinate(type, typeName, memberName, argumentName);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaCoordinate that = (SchemaCoordinate) o;
        return type == that.type
                && Objects.equals(typeName, that.typeName)
                && Objects.equals(memberName, that.memberName)
                && Objects.equals(argumentName, that.argumentName);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Objects.hashCode(typeName);
        result = 31 * result + Objects.hashCode(memberName);
        result = 31 * result + Objects.hashCode(argumentName);
        return result;
    }

    /**
     * This helps you transform the current SchemaCoordinate into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new schema coordinate based on calling build on that builder
     */
    public SchemaCoordinate transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    // -- static factory methods --

    /**
     * Creates a named type coordinate, e.g. {@code User}.
     *
     * @param typeName the name of the type
     *
     * @return a new SchemaCoordinate
     */
    public static SchemaCoordinate namedType(String typeName) {
        assertNotNull(typeName, "typeName is required");
        return new SchemaCoordinate(SchemaCoordinateType.NAMED_TYPE, typeName, null, null);
    }

    /**
     * Creates a field coordinate, e.g. {@code User.name}.
     *
     * @param typeName  the name of the parent type
     * @param fieldName the name of the field
     *
     * @return a new SchemaCoordinate
     */
    public static SchemaCoordinate field(String typeName, String fieldName) {
        assertNotNull(typeName, "typeName is required");
        assertNotNull(fieldName, "fieldName is required");
        return new SchemaCoordinate(SchemaCoordinateType.FIELD, typeName, fieldName, null);
    }

    /**
     * Creates an input field coordinate, e.g. {@code SearchInput.query}.
     *
     * @param typeName       the name of the input type
     * @param inputFieldName the name of the input field
     *
     * @return a new SchemaCoordinate
     */
    public static SchemaCoordinate inputField(String typeName, String inputFieldName) {
        assertNotNull(typeName, "typeName is required");
        assertNotNull(inputFieldName, "inputFieldName is required");
        return new SchemaCoordinate(SchemaCoordinateType.INPUT_FIELD, typeName, inputFieldName, null);
    }

    /**
     * Creates an enum value coordinate, e.g. {@code Status.ACTIVE}.
     *
     * @param typeName      the name of the enum type
     * @param enumValueName the name of the enum value
     *
     * @return a new SchemaCoordinate
     */
    public static SchemaCoordinate enumValue(String typeName, String enumValueName) {
        assertNotNull(typeName, "typeName is required");
        assertNotNull(enumValueName, "enumValueName is required");
        return new SchemaCoordinate(SchemaCoordinateType.ENUM_VALUE, typeName, enumValueName, null);
    }

    /**
     * Creates a field argument coordinate, e.g. {@code Query.searchBusiness(criteria:)}.
     *
     * @param typeName     the name of the parent type
     * @param fieldName    the name of the field
     * @param argumentName the name of the argument
     *
     * @return a new SchemaCoordinate
     */
    public static SchemaCoordinate fieldArgument(String typeName, String fieldName, String argumentName) {
        assertNotNull(typeName, "typeName is required");
        assertNotNull(fieldName, "fieldName is required");
        assertNotNull(argumentName, "argumentName is required");
        return new SchemaCoordinate(SchemaCoordinateType.FIELD_ARGUMENT, typeName, fieldName, argumentName);
    }

    /**
     * Creates a directive coordinate, e.g. {@code @deprecated}.
     *
     * @param directiveName the name of the directive (without the {@code @} prefix)
     *
     * @return a new SchemaCoordinate
     */
    public static SchemaCoordinate directive(String directiveName) {
        assertNotNull(directiveName, "directiveName is required");
        return new SchemaCoordinate(SchemaCoordinateType.DIRECTIVE, directiveName, null, null);
    }

    /**
     * Creates a directive argument coordinate, e.g. {@code @deprecated(reason:)}.
     *
     * @param directiveName the name of the directive (without the {@code @} prefix)
     * @param argumentName  the name of the argument
     *
     * @return a new SchemaCoordinate
     */
    public static SchemaCoordinate directiveArgument(String directiveName, String argumentName) {
        assertNotNull(directiveName, "directiveName is required");
        assertNotNull(argumentName, "argumentName is required");
        return new SchemaCoordinate(SchemaCoordinateType.DIRECTIVE_ARGUMENT, directiveName, null, argumentName);
    }

    /**
     * Creates a new builder for SchemaCoordinate.
     *
     * @return a new builder
     */
    public static Builder newSchemaCoordinate() {
        return new Builder();
    }

    /**
     * Parses a schema coordinate string in the spec-standard format.
     * <p>
     * The parser recognizes the following formats:
     * <ul>
     *   <li>{@code TypeName} - parsed as NAMED_TYPE</li>
     *   <li>{@code TypeName.memberName} - parsed as FIELD (since the member kind is ambiguous)</li>
     *   <li>{@code TypeName.fieldName(argName:)} - parsed as FIELD_ARGUMENT</li>
     *   <li>{@code @directiveName} - parsed as DIRECTIVE</li>
     *   <li>{@code @directiveName(argName:)} - parsed as DIRECTIVE_ARGUMENT</li>
     * </ul>
     *
     * @param coordinate the schema coordinate string to parse
     *
     * @return the parsed SchemaCoordinate
     *
     * @throws AssertException if the coordinate string is null or has an invalid format
     */
    public static SchemaCoordinate parse(String coordinate) {
        assertNotNull(coordinate, "coordinate string is required");
        assertTrue(!coordinate.isEmpty(), () -> "coordinate string must not be empty");
        if (coordinate.startsWith("@")) {
            return parseDirectiveCoordinate(coordinate);
        }
        return parseTypeCoordinate(coordinate);
    }

    // -- Builder --

    /**
     * Builder for {@link SchemaCoordinate}.
     */
    @NullUnmarked
    public static class Builder {

        private SchemaCoordinateType type;
        private String typeName;
        private String memberName;
        private String argumentName;

        private Builder() {
        }

        private Builder(SchemaCoordinate existing) {
            this.type = existing.type;
            this.typeName = existing.typeName;
            this.memberName = existing.memberName;
            this.argumentName = existing.argumentName;
        }

        public Builder type(SchemaCoordinateType type) {
            this.type = type;
            return this;
        }

        public Builder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        public Builder memberName(String memberName) {
            this.memberName = memberName;
            return this;
        }

        public Builder argumentName(String argumentName) {
            this.argumentName = argumentName;
            return this;
        }

        public SchemaCoordinate build() {
            return new SchemaCoordinate(type, typeName, memberName, argumentName);
        }
    }

    // -- private helpers --

    private static String formatCoordinate(SchemaCoordinateType type, @Nullable String typeName,
                                           @Nullable String memberName, @Nullable String argumentName) {
        switch (type) {
            case NAMED_TYPE:
                return String.valueOf(typeName);
            case FIELD:
            case INPUT_FIELD:
            case ENUM_VALUE:
                return typeName + "." + memberName;
            case FIELD_ARGUMENT:
                return typeName + "." + memberName + "(" + argumentName + ":)";
            case DIRECTIVE:
                return "@" + typeName;
            case DIRECTIVE_ARGUMENT:
                return "@" + typeName + "(" + argumentName + ":)";
            default:
                return "SchemaCoordinate{" + type + "}";
        }
    }

    private static SchemaCoordinate parseDirectiveCoordinate(String coordinate) {
        String withoutAt = coordinate.substring(1);
        int parenIndex = withoutAt.indexOf('(');
        if (parenIndex < 0) {
            return directive(withoutAt);
        }
        return parseDirectiveArgumentCoordinate(withoutAt, parenIndex);
    }

    private static SchemaCoordinate parseDirectiveArgumentCoordinate(String withoutAt, int parenIndex) {
        String directiveName = withoutAt.substring(0, parenIndex);
        String argPart = withoutAt.substring(parenIndex + 1);
        assertTrue(argPart.endsWith(":)"), () -> "Invalid directive argument coordinate format, expected '@name(arg:)': " + withoutAt);
        String argName = argPart.substring(0, argPart.length() - 2);
        return directiveArgument(directiveName, argName);
    }

    private static SchemaCoordinate parseTypeCoordinate(String coordinate) {
        int dotIndex = coordinate.indexOf('.');
        if (dotIndex < 0) {
            return namedType(coordinate);
        }
        String typeName = coordinate.substring(0, dotIndex);
        String rest = coordinate.substring(dotIndex + 1);
        int parenIndex = rest.indexOf('(');
        if (parenIndex < 0) {
            return field(typeName, rest);
        }
        return parseFieldArgumentCoordinate(typeName, rest, parenIndex);
    }

    private static SchemaCoordinate parseFieldArgumentCoordinate(String typeName, String rest, int parenIndex) {
        String fieldName = rest.substring(0, parenIndex);
        String argPart = rest.substring(parenIndex + 1);
        assertTrue(argPart.endsWith(":)"), () -> "Invalid field argument coordinate format, expected 'Type.field(arg:)': " + typeName + "." + rest);
        String argName = argPart.substring(0, argPart.length() - 2);
        return fieldArgument(typeName, fieldName, argName);
    }
}
