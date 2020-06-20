package graphql.schema;

import graphql.Internal;

@Internal
public class PropertyDataFetcherHelper {

    private static final PropertyDataFetcherImpl impl = new PropertyDataFetcherImpl(DataFetchingEnvironment.class);

    public static Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType) {
        return impl.getPropertyValue(propertyName, object, graphQLType, null);
    }

    public static Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType, DataFetchingEnvironment environment) {
        return impl.getPropertyValue(propertyName, object, graphQLType, environment);
    }

    public static void clearReflectionCache() {
        impl.clearReflectionCache();
    }

    public static boolean setUseSetAccessible(boolean flag) {
        return impl.setUseSetAccessible(flag);
    }

    public static boolean setUseNegativeCache(boolean flag) {
        return impl.setUseNegativeCache(flag);
    }
}
