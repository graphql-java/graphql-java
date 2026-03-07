package graphql.schema.idl;

import graphql.Assert;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * Simple EnumValuesProvided which maps the GraphQL Enum name to the Java Enum instance.
 */
@PublicApi
@NullMarked
public class NaturalEnumValuesProvider<T extends Enum<T>> implements EnumValuesProvider {


    private final Class<T> enumType;

    public NaturalEnumValuesProvider(Class<T> enumType) {
        Assert.assertNotNull(enumType, "enumType can't be null");
        this.enumType = enumType;
    }

    @Override
    public T getValue(String name) {
        return Enum.valueOf(enumType, name);
    }
}
