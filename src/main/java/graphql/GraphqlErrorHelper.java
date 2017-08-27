package graphql;

import graphql.language.SourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * This little helper allows GraphQlErrors to implement
 * common things (hashcode/ equals ) and to specification more easily
 */
@SuppressWarnings("SimplifiableIfStatement")
public class GraphqlErrorHelper {

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

    public static Object locations(List<SourceLocation> locations) {
        return locations.stream().map(GraphqlErrorHelper::location).collect(toList());
    }

    public static Object location(SourceLocation location) {
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
