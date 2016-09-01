package graphql.schema;

/**
 * Provides supplier functionality for those using java 8 without breaking
 * the contract of jdk 6. Used in a function-esque lambda pattern to
 * build graphql types. e.g:
 * <pre>
 * {@code
 *  GraphQLObjectType.Builder obj = GraphQLObjectType.newObject();
 *     obj.field(field -> field
 *             .name("fieldName")
 *             .argument(arg -> arg
 *                     .name("argumentName")));
 *  }
 * </pre>
 *
 * @param <T> type of result supplied
 */
public interface BuilderFunction<T> {
    T apply(T t);
}
