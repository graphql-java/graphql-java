package graphql;

import org.jspecify.annotations.NullMarked;

/**
 * Errors in graphql-java can have a classification to help with the processing
 * of errors.  Custom {@link graphql.GraphQLError} implementations could use
 * custom classifications.
 * <p>
 * graphql-java ships with a standard set of error classifications via {@link graphql.ErrorType}
 */
@PublicApi
@NullMarked
public interface ErrorClassification {

    /**
     * This is called to create a representation of the error classification
     * that can be put into the `extensions` map of the graphql error under the key 'classification'
     * when {@link GraphQLError#toSpecification()} is called
     *
     * @param error the error associated with this classification
     *
     * @return an object representation of this error classification
     */
    @SuppressWarnings("unused")
    default Object toSpecification(GraphQLError error) {
        return String.valueOf(this);
    }

    /**
     * This produces a simple ErrorClassification that represents the provided String.  You can
     * use this factory method to give out simple but custom error classifications.
     *
     * @param errorClassification the string that represents the error classification
     *
     * @return a ErrorClassification that is that provided string
     */
    static ErrorClassification errorClassification(String errorClassification) {
        return new ErrorClassification() {
            @Override
            public String toString() {
                return errorClassification;
            }
        };
    }
}
