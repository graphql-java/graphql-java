package graphql;


import graphql.language.SourceLocation;

import java.util.List;

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

        String message;
        List<SourceLocation> locations;
        ErrorType errorType;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Helper helper = (Helper) o;

            if (message != null ? !message.equals(helper.message) : helper.message != null) return false;
            if (locations != null ? !locations.equals(helper.locations) : helper.locations != null) return false;
            return errorType == helper.errorType;
        }

        @Override
        public int hashCode() {
            int result = message != null ? message.hashCode() : 0;
            result = 31 * result + (locations != null ? locations.hashCode() : 0);
            result = 31 * result + errorType.hashCode();
            return result;
        }

        public static int hashCode(GraphQLError dis) {
            int result = dis.getMessage() != null ? dis.getMessage().hashCode() : 0;
            result = 31 * result + (dis.getLocations() != null ? dis.getLocations().hashCode() : 0);
            result = 31 * result + dis.getErrorType().hashCode();
            return result;
        }

        public static boolean equals(GraphQLError dis, GraphQLError dat) {
            if (dis == dat) {
                return true;
            }
            if (dis.getMessage() != null ? !dis.getMessage().equals(dat.getMessage()) : dat.getMessage() != null)
                return false;
            if (dis.getLocations() != null ? !dis.getLocations().equals(dat.getLocations()) : dat.getLocations() != null)
                return false;
            return dis.getErrorType() == dat.getErrorType();
        }
    }

}
