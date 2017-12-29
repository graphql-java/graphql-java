package example.http;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.InvalidSyntaxError;
import graphql.SerializationError;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.MissingRootTypeException;
import graphql.execution.NonNullableFieldWasNullError;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.introspection.Introspection;
import graphql.language.SourceLocation;
import graphql.schema.CoercingSerializeException;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * This allows manual (not automated yet) testing of Jackson / Gson output of execution results
 */
public class ExecutionResultJSONTesting {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final Gson GSON = new GsonBuilder()
            //
            // This is important because the graphql spec says that null values should be present
            //
            .serializeNulls()
            .create();

    public ExecutionResultJSONTesting(String target, HttpServletResponse response) {

        try {
            Object result = createER();
            if (target.contains("spec")) {
                result = createER().toSpecification();
            }

            if (target.contains("gson")) {
                testGson(response, result);
            } else {
                testJackson(response, result);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void testJackson(HttpServletResponse response, Object er) throws IOException {
        OBJECT_MAPPER.writeValue(response.getWriter(), er);
    }

    private void testGson(HttpServletResponse response, Object er) throws IOException {
        GSON.toJson(er, response.getWriter());
    }

    private ExecutionResult createER() {
        List<GraphQLError> errors = new ArrayList<>();

        errors.add(new ValidationError(ValidationErrorType.UnknownType, mkLocations(), "Test ValidationError"));
        errors.add(new MissingRootTypeException("Mutations are not supported.", null));
        errors.add(new InvalidSyntaxError(mkLocations(), "Not good syntax m'kay"));
        errors.add(new NonNullableFieldWasNullError(new NonNullableFieldWasNullException(mkTypeInfo(), mkPath())));
        errors.add(new SerializationError(mkPath(), new CoercingSerializeException("Bad coercing")));
        errors.add(new ExceptionWhileDataFetching(mkPath(), new RuntimeException("Bang"), mkLocation(666, 999)));

        return new ExecutionResultImpl(null, errors);
    }

    private List<SourceLocation> mkLocations() {
        return stream(new SourceLocation[]{mkLocation(666, 999), mkLocation(333, 0)}).collect(toList());
    }

    private SourceLocation mkLocation(int line, int column) {
        return new SourceLocation(line, column);
    }

    private ExecutionPath mkPath() {
        return ExecutionPath.rootPath().segment("heroes").segment(0).segment("abilities").segment("speed").segment(4);
    }

    private ExecutionTypeInfo mkTypeInfo() {
        return ExecutionTypeInfo.newTypeInfo()
                .type(Introspection.__Schema)
                .path(mkPath())
                .build();
    }
}
