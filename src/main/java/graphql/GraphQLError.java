package graphql;


import graphql.language.SourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

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

    /**
     * @return an enum classifying this error
     */
    ErrorType getErrorType();

    /**
     * The graphql spec says that the (optional) path field of any error should be a list
     * of path entries - http://facebook.github.io/graphql/#sec-Errors
     *
     * @return the path in list format
     */
    default List<Object> getPath() {
        return null;
    }

    /**
     * The graphql specification says that result of a call should be a map that follows certain rules on what items
     * should be present.  Certain JSON serializers may or may interpret the error to spec, so this method
     * is provided to produce a map that strictly follows the specification.
     *
     * See : <a href="http://facebook.github.io/graphql/#sec-Errors">http://facebook.github.io/graphql/#sec-Errors</a>
     *
     * @return a map of the error that strictly follows the specification
     */
    default Map<String, Object> toSpecification() {
        return Helper.toSpecification(this);
    }

    /**
     * @return a map of error extensions or null if there are none
     */
    default Map<Object, Object> getExtensions() {
        return null;
    }


    /**
     * This little helper allows GraphQlErrors to implement
     * common things (hashcode/ equals ) and to specification more easily
     */
    @SuppressWarnings("SimplifiableIfStatement")
    class Helper {

        public static Map<String, Object> toSpecification(GraphQLError error) {
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("message", error.getMessage());
            if (error.getLocations() != null) {
                errorMap.put("locations", locations(error.getLocations()));
            }
            if (error.getPath() != null) {
                errorMap.put("path", error.getPath());
            }
            if (error.getExtensions() != null) {
                errorMap.put("extensions", error.getExtensions());
            }
            return errorMap;
        }

        private static Object locations(List<SourceLocation> locations) {
            return locations.stream().map(Helper::location).collect(toList());
        }

        private static Object location(SourceLocation location) {
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("line", location.getLine());
            map.put("column", location.getColumn());
            return map;
        }

        public static int hashCode(GraphQLError dis) {
            int result = dis.getMessage() != null ? dis.getMessage().hashCode() : 0;
            result = 31 * result + (dis.getLocations() != null ? dis.getLocations().hashCode() : 0);
            result = 31 * result + (dis.getPath() != null ? dis.getPath().hashCode() : 0);
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
            if (dis.getPath() != null ? !dis.getPath().equals(dat.getPath()) : dat.getPath() != null)
                return false;
            return dis.getErrorType() == dat.getErrorType();
        }
    }

}
