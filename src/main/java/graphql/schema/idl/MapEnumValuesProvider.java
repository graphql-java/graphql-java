package graphql.schema.idl;

import graphql.Assert;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@PublicApi
@NullMarked
public class MapEnumValuesProvider implements EnumValuesProvider {


    private final Map<String, Object> values;

    public MapEnumValuesProvider(Map<String, Object> values) {
        Assert.assertNotNull(values, "values can't be null");
        this.values = values;
    }

    @Override
    public @Nullable Object getValue(String name) {
        return values.get(name);
    }
}
