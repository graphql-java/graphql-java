package graphql.execution.conversion;

/**
 * An argument converter is responsible for converting a {@link graphql.schema.GraphQLArgument} value into some other
 * object representation such as a POJO.  This allows later {@link graphql.schema.DataFetcher} code to assume a specific
 * argument object shape.
 *
 * By default graphql will receive a complex {@link graphql.schema.GraphQLInputObjectType} as a map of values.  You can use
 * this interface to convert this into a type safe POJO say which will then be passed onto DataFetcher code.
 */
public interface ArgumentConverter {

    /**
     * This is called with the object to convert and extra type information about tbe argument being converted.  If the converter
     * cannot or does not want to convert this object then it MUST return {@link graphql.execution.conversion.ArgumentConverterEnvironment#getSourceObject()}
     * so another converter in the chain may have a go.
     *
     * @param environment the conversion environment
     *
     * @return a new object or {@link graphql.execution.conversion.ArgumentConverterEnvironment#getSourceObject()} if you don't want to convert it
     */
    Object convertArgument(ArgumentConverterEnvironment environment);
}
