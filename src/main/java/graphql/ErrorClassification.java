package graphql;

/**
 * Errors in graphql-java can have a classification to help with the processing
 * of errors.  Custom {@link graphql.GraphQLError} implementations could use
 * custom classifications.
 * <p>
 * graphql-java ships with a standard set of error classifications via {@link graphql.ErrorType}
 */
@PublicApi
public interface ErrorClassification {
}
