package graphql.schema.idl;

import graphql.Assert;
import graphql.schema.GraphQLType;

/**
 * Defines the scope to control where the registered {@code Comparator} can be applied.
 * <p></p>
 * {@code elementType}s can be ordered within its {@code parentType} to restrict the {@code Comparator}s scope of operation.
 * Otherwise supplying only the {@code elementType} results in the {@code Comparator} being reused across all matching {@code GraphQLType}s regardless of parent.
 */
public class SchemaPrinterComparatorEnvironment {

    private Class<? extends GraphQLType> parentType;

    private Class<? extends GraphQLType> elementType;

    private SchemaPrinterComparatorEnvironment(Class<? extends GraphQLType> parentType, Class<? extends GraphQLType> elementType) {
        Assert.assertNotNull(elementType, "elementType can't be null");
        this.parentType = parentType;
        this.elementType = elementType;
    }

    /**
     * @return The parent type or {@code null} if not supplied.
     */
    public Class<? extends GraphQLType> getParentType() {
        return parentType;
    }

    /**
     * @return A new environment containing only the element type.
     */
    public SchemaPrinterComparatorEnvironment withElementTypeOnly() {
        return new SchemaPrinterComparatorEnvironment(null, elementType);
    }

    /**
     * @return The valid element type.
     */
    public Class<? extends GraphQLType> getElementType() {
        return elementType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SchemaPrinterComparatorEnvironment that = (SchemaPrinterComparatorEnvironment) o;

        if (parentType != null ? !parentType.equals(that.parentType) : that.parentType != null) {
            return false;
        }
        return elementType.equals(that.elementType);
    }

    @Override
    public int hashCode() {
        int result = parentType != null ? parentType.hashCode() : 0;
        result = 31 * result + elementType.hashCode();
        return result;
    }

    public static Builder newEnvironment() {
        return new Builder();
    }

    public static class Builder {

        private Class<? extends GraphQLType> parentType;

        private Class<? extends GraphQLType> elementType;

        public <T extends GraphQLType> Builder withParentType(Class<T> parentType) {
            this.parentType = parentType;
            return this;
        }

        public <T extends GraphQLType> Builder withElementType(Class<T> elementType) {
            this.elementType = elementType;
            return this;
        }

        public SchemaPrinterComparatorEnvironment build() {
            return new SchemaPrinterComparatorEnvironment(parentType, elementType);
        }
    }
}
