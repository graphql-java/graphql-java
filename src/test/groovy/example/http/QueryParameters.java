package example.http;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Graphql clients can send GET or POST HTTP requests.  The spec does not make an explicit
 * distinction.  So you may need to handle both.  The following was tested using
 * a graphiql client tool found here : https://github.com/skevy/graphiql-app
 *
 * You should consider bundling graphiql in your application
 *
 * https://github.com/graphql/graphiql
 *
 * This outlines more information on how to handle parameters over http
 *
 * http://graphql.org/learn/serving-over-http/
 */
class QueryParameters {

    String query;
    String operationName;
    Map<String, Object> variables = Collections.emptyMap();

    public String getQuery() {
        return query;
    }

    public String getOperationName() {
        return operationName;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    static QueryParameters from(HttpServletRequest request) {
        QueryParameters parameters = new QueryParameters();
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            //
            // the graphql guidance says  :
            //
            // If the "application/graphql" Content-Type header is present, treat
            // the HTTP POST body contents as the GraphQL query string.
            if ("application/graphql".equals(request.getContentType())) {
                parameters.query = readPostBody(request);
            } else {
                Map<String, Object> json = readJSON(request);

                //
                // the graphql guidance says  :
                //
                //  - If the "query" query string parameter is present
                //  - ..., it should be parsed and handled
                //  - in the same way as the HTTP GET case.
                //
                Optional<String> queryStr = getQueryQueryParameter(request);
                parameters.query = queryStr.orElseGet(() -> (String) json.get("query"));
                parameters.operationName = (String) json.get("operationName");
                parameters.variables = getVariablesFromPost(json.get("variables"));
            }
        } else {
            parameters.query = request.getParameter("query");
            parameters.operationName = request.getParameter("operationName");
            parameters.variables = getVariablesFromPost(request.getParameter("variables"));
        }
        return parameters;
    }

    private static Optional<String> getQueryQueryParameter(HttpServletRequest request) {
        // http servlet spec getParameter() does not distinguish between POST body or GET query strings
        // so we have to parse for it in this case
        return HttpKit.getQueryParameter(request,"query");
    }

    private static Map<String, Object> getVariablesFromPost(Object variables) {
        if (variables instanceof Map) {
            Map<?, ?> inputVars = (Map) variables;
            Map<String, Object> vars = new HashMap<>();
            inputVars.forEach((k, v) -> vars.put(String.valueOf(k), v));
            return vars;
        }
        return JsonKit.toMap(String.valueOf(variables));
    }

    private static Map<String, Object> readJSON(HttpServletRequest request) {
        String s = readPostBody(request);
        return JsonKit.toMap(s);
    }

    private static String readPostBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            int c;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
