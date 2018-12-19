package graphql.execution.conversion;

import graphql.PublicApi;

/**
 * An argument converter is responsible for converting a {@link graphql.schema.GraphQLArgument} value into some other
 * object representation such as a POJO.  This allows later {@link graphql.schema.DataFetcher} code to assume a specific
 * argument object shape.
 *
 * By default graphql will receive a complex {@link graphql.schema.GraphQLInputObjectType} as a map of values.  You can use
 * this interface to convert this into a type safe POJO say which will then be passed onto DataFetcher code.
 *
 * Uou might use your favourite object mapping framework for conversion such as Jackson or GSON, graphql-java doesnt care, it only cares that you
 * take a basic object and give back the converted one for use further down the execution
 *
 * The inputs you will receive as as follows :
 *
 * <ul>
 * <li> input object type - a java.util.Map of values</li>
 * <li> list of types - a java.util.List of values</li>
 * <li> scalar - the underlying java object that scalar coercing function returns, such as String or Integer</li>
 * <li> enums - the underlying java object that enum coercing function returns</li>
 * </ul>
 *
 * In general you should not need to convert scalars and enums since they have {@link graphql.schema.Coercing} functions.  This interface
 * is most powerful when you have {@link graphql.schema.GraphQLInputObjectType} types as argument values.
 */
@PublicApi
public interface ArgumentConverter {

    /**
     * This is called with the object to convert and extra type information about tbe argument being converted.  If the converter
     * cannot or does not want to convert this object then it MUST return {@link graphql.execution.conversion.ArgumentConverterEnvironment#getValueToBeConverted()}
     * so another converter in the chain may have a go.
     *
     * @param environment the conversion environment
     *
     * @return a new object or {@link graphql.execution.conversion.ArgumentConverterEnvironment#getValueToBeConverted()} if you don't want to convert it
     */
    Object convertArgument(ArgumentConverterEnvironment environment);
}
