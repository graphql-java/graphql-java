package graphql.schema;

import graphql.AssertException;
import graphql.PublicApi;

import java.util.Objects;

import static graphql.Assert.assertTrue;
import static graphql.Assert.assertValidName;

/**
 * A field in graphql is uniquely located within a parent type and hence code elements
 * like {@link graphql.schema.DataFetcher} need to be specified using those coordinates.
 */
@PublicApi
public class FieldCoordinates {

    private final boolean systemCoordinates;
    private final String typeName;
    private final String fieldName;

    private FieldCoordinates(String typeName, String fieldName, boolean systemCoordinates) {
        this.typeName = typeName;
        this.fieldName = fieldName;
        this.systemCoordinates = systemCoordinates;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public boolean isSystemCoordinates() {
        return systemCoordinates;
    }

    /**
     * Checks the validity of the field coordinate names.  The validity checks vary by coordinate type.  Standard
     * coordinates validate both the {@code typeName} and {@code fieldName}, while system coordinates do not have
     * a parent so they only validate the {@code fieldName}.
     *
     * @throws AssertException if the coordinates are NOT valid; otherwise, returns normally.
     */
    public void assertValidNames() throws AssertException {
        if (systemCoordinates) {
            assertTrue((null != fieldName) &&
                    fieldName.startsWith("__"), () -> "Only __ system fields can be addressed without a parent type");
            assertValidName(fieldName);
        } else {
            assertValidName(typeName);
            assertValidName(fieldName);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FieldCoordinates that = (FieldCoordinates) o;
        return Objects.equals(typeName, that.typeName) &&
                Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(typeName);
        result = 31 * result + Objects.hashCode(fieldName);
        return result;
    }

    @Override
    public String toString() {
        return typeName + '.' + fieldName;
    }

    /**
     * Creates new field coordinates
     *
     * @param parentType      the container of the field
     * @param fieldDefinition the field definition
     *
     * @return new field coordinates represented by the two parameters
     */
    public static FieldCoordinates coordinates(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition) {
        return new FieldCoordinates(parentType.getName(), fieldDefinition.getName(), false);
    }

    /**
     * Creates new field coordinates
     *
     * @param parentType the container of the field
     * @param fieldName  the field name
     *
     * @return new field coordinates represented by the two parameters
     */
    public static FieldCoordinates coordinates(String parentType, String fieldName) {
        return new FieldCoordinates(parentType, fieldName, false);
    }

    /**
     * Creates new field coordinates
     *
     * @param parentType the container of the field
     * @param fieldName  the field name
     *
     * @return new field coordinates represented by the two parameters
     */
    public static FieldCoordinates coordinates(GraphQLFieldsContainer parentType, String fieldName) {
        return coordinates(parentType.getName(), fieldName);
    }

    /**
     * The exception to the general rule is the system __xxxx Introspection fields which have no parent type and
     * are able to be specified on any type
     *
     * @param fieldName the name of the system field which MUST start with __
     *
     * @return the coordinates
     */
    public static FieldCoordinates systemCoordinates(String fieldName) {
        return new FieldCoordinates(null, fieldName, true);
    }
}
