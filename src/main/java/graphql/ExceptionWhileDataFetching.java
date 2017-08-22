package graphql;


import graphql.execution.ExecutionPath;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static java.lang.String.format;

@PublicApi
public class ExceptionWhileDataFetching implements CustomGraphQLError {

    private final ExecutionPath path;
    private final Throwable exception;
    private final SourceLocation sourceLocation;

    public ExceptionWhileDataFetching(ExecutionPath path, Throwable exception, SourceLocation sourceLocation) {
        this.path = assertNotNull(path);
        this.exception = assertNotNull(exception);
        this.sourceLocation = sourceLocation;
    }

    public Throwable getException() {
        return exception;
    }


    @Override
    public String getMessage() {
        return format("Exception while fetching data (%s) : %s", path, exception.getMessage());
    }

    @Override
    public List<SourceLocation> getLocations() {
        return Collections.singletonList(sourceLocation);
    }

    /**
     * The graphql spec says that that path field of any error should be a list
     * of path entries - http://facebook.github.io/graphql/#sec-Errors
     *
     * @return the path in list format
     */
    public List<Object> getPath() {
        return path.toList();
    }


    @Override
    public Map<String, ?> getCustomData() {
        Map<String,Object> customData = new HashMap<>();
        
        if( exception instanceof CustomGraphQLErrorSupport ){
            customData.putAll(((CustomGraphQLErrorSupport)exception).getErrors());
        }        
        customData.put("path",getPath());
        return customData;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.DataFetchingException;
    }

    @Override
    public String toString() {
        return "ExceptionWhileDataFetching{" +
                "path=" + path +
                "exception=" + exception +
                "sourceLocation=" + sourceLocation +
                '}';
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return Helper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Helper.hashCode(this);
    }
}
