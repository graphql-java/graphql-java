package graphql.normalized;

import graphql.language.Value;

import java.util.Objects;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.Assert.assertValidName;
import static graphql.language.AstPrinter.printAst;

/**
 * A value with type information.
 */
public class NormalizedInputValue {
    private final String typeName;
    private final Object value;

    public NormalizedInputValue(String typeName, Object value) {
        this.typeName = assertValidTypeName(typeName);
        this.value = value;
    }

    private String assertValidTypeName(String typeName) {
        assertValidName(unwrapAll(typeName));
        return typeName;
    }

    private String unwrapAll(String typeName) {
        String result = unwrapOne(typeName);
        while (isWrapped(result)) {
            result = unwrapOne(result);
        }
        return result;
    }

    /**
     * This can be a wrapped type: e.g. [String!]!
     *
     * @return the type name
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * @return the type name unwrapped of all list and non-null type wrapping
     */
    public String getUnwrappedTypeName() {
        return unwrapAll(typeName);
    }

    /**
     * Depending on the type it returns:
     * Scalar or Enum: the ast literal of the Scalar.
     * InputObject: the value is a map of field-name to NormalizedInputValue
     * List of Scalar literal or Enum literal or NormalizedInput (or even List of List ..)
     *
     * @return the value
     */
    public Object getValue() {
        return value;
    }


    /**
     * @return true if the input value type is a list or a non-nullable list
     */
    public boolean isListLike() {
        return typeName.startsWith("[");
    }

    /**
     * @return true if the input value type is non-nullable
     */
    public boolean isNonNullable() {
        return typeName.endsWith("!");
    }

    /**
     * @return true if the input value type is nullable
     */
    public boolean isNullable() {
        return !isNonNullable();
    }

    private boolean isWrapped(String typeName) {
        return typeName.endsWith("!") || isListOnly(typeName);
    }

    private boolean isListOnly(String typeName) {
        if (typeName.endsWith("!")) {
            return false;
        }
        return typeName.startsWith("[") || typeName.endsWith("]");
    }

    private String unwrapOne(String typeName) {
        assertNotNull(typeName);
        assertTrue(typeName.trim().length() > 0, () -> "We have an empty type name unwrapped");
        if (typeName.endsWith("!")) {
            return typeName.substring(0, typeName.length() - 1);
        }
        if (isListOnly(typeName)) {
            // nominally this will never be true - but better to be safe than sorry
            assertTrue(typeName.startsWith("["), () -> String.format("We have a unbalanced list type string '%s'", typeName));
            assertTrue(typeName.endsWith("]"), () -> String.format("We have a unbalanced list type string '%s'", typeName));

            return typeName.substring(1, typeName.length() - 1);
        }
        return typeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NormalizedInputValue that = (NormalizedInputValue) o;
        if (value instanceof Value && that.value instanceof Value) {
            return Objects.equals(typeName, that.typeName) && Objects.equals(printAst((Value) value), printAst((Value) that.value));
        }
        return Objects.equals(typeName, that.typeName) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName, value);
    }

    @Override
    public String toString() {
        return "NormalizedInputValue{" +
                "typeName='" + typeName + '\'' +
                ", value=" + value +
                '}';
    }
}
