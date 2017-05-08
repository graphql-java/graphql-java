package graphql;


import graphql.language.SourceLocation;

import java.util.List;

@PublicApi
public interface GraphQLError {

    String getMessage();

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
