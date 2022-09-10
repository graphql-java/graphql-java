package graphql.schema;

import graphql.Internal;

import java.util.function.Supplier;

/**
 * This class is the guts of a property data fetcher and also used in AST code to turn
 * in memory java objects into AST elements
 */
@Internal
public class PropertyDataFetcherHelper {

    private static final PropertyFetchingImpl impl = new PropertyFetchingImpl(DataFetchingEnvironment.class);

    public static Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType) {
        return impl.getPropertyValue(propertyName, object, graphQLType, false, () -> null);
    }

    public static Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType, Supplier<DataFetchingEnvironment> environment) {
        return impl.getPropertyValue(propertyName, object, graphQLType, true, environment::get);
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
