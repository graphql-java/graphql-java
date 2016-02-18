package graphql.schema;

import java.util.Map;

public abstract class AbstractReflectionDataFetcher
    implements DataFetcher {

    protected final String name;

    protected AbstractReflectionDataFetcher(
        String name) {

        this.name = name;

    }

    @Override
    public final Object get(
        DataFetchingEnvironment environment) {

        Object source = environment.getSource();
        if (source == null)
            return null;
        if (source instanceof Map) {
            return ((Map<?, ?>) source).get(name);
        }
        return getValue(source, environment.getFieldType());
    }

    protected abstract Object getValue(
        Object target,
        GraphQLOutputType outputType);

}
