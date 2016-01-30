package graphql.schema;

import static graphql.Scalars.GraphQLBoolean;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class PropertyDataFetcher
    extends AbstractReflectionDataFetcher {

    public PropertyDataFetcher(
        String propertyName) {

        super(propertyName);
    }

    private boolean isBooleanProperty(
        GraphQLOutputType outputType) {

        if (outputType == GraphQLBoolean)
            return true;
        if (outputType instanceof GraphQLNonNull) {
            return ((GraphQLNonNull) outputType).getWrappedType() == GraphQLBoolean;
        }
        return false;
    }

    @Override
    protected Object getValue(
        Object target,
        GraphQLOutputType outputType) {

        String prefix = isBooleanProperty(outputType) ? "is" : "get";
        String getterName = prefix + name.substring(0, 1).toUpperCase() + name.substring(1);
        try {
            Method method = target.getClass().getMethod(getterName);
            return method.invoke(target);

        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
