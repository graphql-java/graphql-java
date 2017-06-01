package example.http;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Optional;

public class HttpKit {
    /**
     * We should not have to write this method but to get the query string ONLY parameter
     * in Http Servlet spec world means parsing the query string on POST
     *
     * @param request       the http servlet request
     * @param parameterName the name of the parameter
     *
     * @return an optional parameter
     */
    public static Optional<String> getQueryParameter(HttpServletRequest request, String parameterName) {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            parameterName = parameterName + "=";
            String queryString = request.getQueryString();
            if (queryString == null) {
                return Optional.empty();
            }
            int index = queryString.indexOf(parameterName);
            if (index == -1) {
                return Optional.empty();
            }
            StringBuilder sb = new StringBuilder();
            index += parameterName.length();
            while (index < queryString.length()) {
                char c = queryString.charAt(index);
                if (c == '&') {
                    break;
                }
                sb.append(c);
                index++;
            }
            try {
                return Optional.of(URLDecoder.decode(sb.toString(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        } else {
            return Optional.ofNullable(request.getParameter(parameterName));
        }
    }
}
