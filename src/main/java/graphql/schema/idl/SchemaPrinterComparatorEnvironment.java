package graphql.schema.idl;

import graphql.Assert;
import graphql.PublicApi;
import graphql.schema.GraphQLType;

import java.util.function.Consumer;

/**
 * Defines the scope to control where the registered {@code Comparator} can be applied.
 * <p>
 * {@code elementType}s can be ordered within its {@code parentType} to restrict the {@code Comparator}s scope of operation.
 * Otherwise supplying only the {@code elementType} results in the {@code Comparator} being reused across all matching {@code GraphQLType}s regardless of parent.
 */
@PublicApi
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
     * @return The valid element type.
     */
    public Class<? extends GraphQLType> getElementType() {
        return elementType;
    }

    /**
     * This helps you transform the current {@code SchemaPrinterComparatorEnvironment} into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform.
     * @return a new object based on calling build on that builder.
     */
    public SchemaPrinterComparatorEnvironment transform(Consumer<SchemaPrinterComparatorEnvironment.Builder> builderConsumer) {
        Builder builder = newEnvironment(this);
        builderConsumer.accept(builder);
        return builder.build();
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

    public static Builder newEnvironment(SchemaPrinterComparatorEnvironment existing) {
        return new Builder(existing);
    }

    public static class Builder {

        private Class<? extends GraphQLType> parentType;

        private Class<? extends GraphQLType> elementType;

        public Builder() {
        }

        public Builder(SchemaPrinterComparatorEnvironment existing) {
            this.parentType = existing.parentType;
            this.elementType = existing.elementType;
        }

        public <T extends GraphQLType> Builder parentType(Class<T> parentType) {
            this.parentType = parentType;
            return this;
        }

        public <T extends GraphQLType> Builder elementType(Class<T> elementType) {
            this.elementType = elementType;
            return this;
        }

        public SchemaPrinterComparatorEnvironment build() {
            return new SchemaPrinterComparatorEnvironment(parentType, elementType);
        }
    }
}