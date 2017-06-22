package graphql;


import graphql.language.SourceLocation;

import java.util.List;

/**
 * @see <a href="https://facebook.github.io/graphql/#sec-Errors">GraphQL Spec - 7.2.2 Errors</a>
 */
@PublicApi
public interface GraphQLError {

    /**
     * @return a description of the error intended for the developer as a guide to understand and correct the error
     */
    String getMessage();

    /**
     * @return the location(s) within the GraphQL document at which the error occurred. Each {@link SourceLocation}
     * describes the beginning of an associated syntax element
     */
    List<SourceLocation> getLocations();

    ErrorType getErrorType();


    /**
     * This little helper allows GraphQlErrors to implement
     * common things (hashcode/ equals) more easily
     */
    @SuppressWarnings("SimplifiableIfStatement")
    class Helper {

        public static int hashCode(GraphQLError dis) {
            int result = dis.getMessage() != null ? dis.getMessage().hashCode() : 0;
            result = 31 * result + (dis.getLocations() != null ? dis.getLocations().hashCode() : 0);
            result = 31 * result + dis.getErrorType().hashCode();
            return result;
        }

        public static boolean equals(GraphQLError dis, Object o) {
            if (dis == o) {
                return true;
            }
            if (o == null || dis.getClass() != o.getClass()) return false;

            GraphQLError dat = (GraphQLError) o;

            if (dis.getMessage() != null ? !dis.getMessage().equals(dat.getMessage()) : dat.getMessage() != null)
                return false;
            if (dis.getLocations() != null ? !dis.getLocations().equals(dat.getLocations()) : dat.getLocations() != null)
                return false;
            return dis.getErrorType() == dat.getErrorType();
        }
    }

}
