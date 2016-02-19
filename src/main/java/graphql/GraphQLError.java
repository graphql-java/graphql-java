package graphql;


import graphql.language.SourceLocation;

import java.util.List;

/**
 * <p>GraphQLError interface.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public interface GraphQLError {

    /**
     * <p>getMessage.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    String getMessage();

    /**
     * <p>getLocations.</p>
     *
     * @return a {@link java.util.List} object.
     */
    List<SourceLocation> getLocations();

    /**
     * <p>getErrorType.</p>
     *
     * @return a {@link graphql.ErrorType} object.
     */
    ErrorType getErrorType();

}
