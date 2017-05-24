package graphql.schema.idl;

import graphql.Assert;
import graphql.PublicApi;

/**
 * Simple EnumValuesProvided which maps the GraphQL Enum name to the Java Enum instance.
 *
 * @param <T>
 */
@PublicApi
public class StaticEnumValuesProvider<T extends Enum<T>> implements EnumValuesProvider {


    private final Class<T> enumType;

    public StaticEnumValuesProvider(Class<T> enumType) {
        Assert.assertNotNull(enumType, "enumType can't be null");
        this.enumType = enumType;
    }

    @Override
    public T getValue(String name) {
        return Enum.valueOf(enumType, name);
    }
}
