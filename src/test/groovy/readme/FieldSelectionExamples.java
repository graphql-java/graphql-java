package readme;

import graphql.ExecutionInput;
import graphql.normalized.NormalizedDocument;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;

import java.util.List;
import java.util.Map;

@SuppressWarnings({"unused", "Convert2Lambda"})
public class FieldSelectionExamples {

    ExecutionInput enableLookahead(String query) {
        return ExecutionInput.newExecutionInput(query)
                .graphQLContext(Map.of(NormalizedDocument.ENABLED, true)).build();
    }

    void usingSelectionSet() {
        DataFetcher smartUserDF = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment env) {
                String userId = env.getArgument("userId");

                DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();
                if (selectionSet.contains("friends/*")) {
                    return getUserAndTheirFriends(userId);
                } else {
                    return getUser(userId);
                }
            }
        };
    }

    DataFetchingEnvironment env;

    void getFields() {
        DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();
        List<SelectedField> nodeFields = selectionSet.getFields("edges/nodes/*");
        nodeFields.forEach(selectedField -> {
            System.out.println(selectedField.getName());
            System.out.println(selectedField.getType());

            DataFetchingFieldSelectionSet innerSelectionSet = selectedField.getSelectionSet();
            // .. this forms a tree of selection and you can get very fancy with it
        });
    }

    private Object getUser(String userId) {
        return null;
    }

    private Object getUserAndTheirFriends(String userId) {
        return null;
    }
}
