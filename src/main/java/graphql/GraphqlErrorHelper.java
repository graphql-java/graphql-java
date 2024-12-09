package graphql;

import graphql.language.SourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static graphql.collect.ImmutableKit.mapAndDropNulls;

/**
 * This little helper allows GraphQlErrors to implement
 * common things (hashcode/ equals ) and to specification more easily
 */
@SuppressWarnings("SimplifiableIfStatement")
@Internal
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

        Map<String, Object> extensions = error.getExtensions();
        ErrorClassification errorClassification = error.getErrorType();
        //
        // we move the ErrorClassification into extensions which allows
        // downstream people to see them but still be spec compliant
        if (errorClassification != null) {
            if (extensions != null) {
                extensions = new LinkedHashMap<>(extensions);
            } else {
                extensions = new LinkedHashMap<>();
            }
            // put in the classification unless it's already there
            if (!extensions.containsKey("classification")) {
                extensions.put("classification", errorClassification.toSpecification(error));
            }
        }

        if (extensions != null) {
            errorMap.put("extensions", extensions);
        }
        return errorMap;
    }

    public static Object locations(List<SourceLocation> locations) {
        return mapAndDropNulls(locations, GraphqlErrorHelper::location);
    }

    /**
     *  Positive integers starting from 1 required for error locations,
     *  from the spec <a href="https://spec.graphql.org/draft/#sec-Errors.Error-Result-Format">...</a>
     *
     * @param location the source location in play
     * @return  a value for source location of the error
     */
    public static Object location(SourceLocation location) {
        int line = location.getLine();
        int column = location.getColumn();
        if (line < 1 || column < 1) {
            return null;
        }
        return Map.of("line", line, "column", column);
    }

    public static int hashCode(GraphQLError dis) {
        int result = 1;
        result = 31 * result + Objects.hashCode(dis.getMessage());
        result = 31 * result + Objects.hashCode(dis.getLocations());
        result = 31 * result + Objects.hashCode(dis.getPath());
        result = 31 * result + Objects.hashCode(dis.getErrorType());
        return result;
    }

    public static boolean equals(GraphQLError dis, Object o) {
        if (dis == o) {
            return true;
        }
        if (o == null || dis.getClass() != o.getClass()) return false;

        GraphQLError dat = (GraphQLError) o;

        if (!Objects.equals(dis.getMessage(), dat.getMessage())) {
            return false;
        }
        if (!Objects.equals(dis.getLocations(), dat.getLocations())) {
            return false;
        }
        if (!Objects.equals(dis.getPath(), dat.getPath())) {
            return false;
        }
        return dis.getErrorType() == dat.getErrorType();
    }
}
