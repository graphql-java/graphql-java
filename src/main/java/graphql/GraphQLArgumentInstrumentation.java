package graphql;

import graphql.schema.GraphQLArgument;

/**
 * You can add an instance of this class to your {@link GraphQLArgument}.
 * The instrumentValue method will be called every time a argument value is being resolved in the {@link graphql.execution.ValuesResolver}.
 * For example, if the client passes an argument value "hello" to an argument in your field, you can write an instrumentValue implementation that transforms it to upper case letters.
 * Then, the data fetcher environment will contain a value of "HELLO".
 */
public interface GraphQLArgumentInstrumentation {
    Object instrumentValue(Object argumentValue);
}
