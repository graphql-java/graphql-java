package graphql.normalized;

import graphql.Internal;
import graphql.language.Document;
import graphql.language.NodeUtil;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Internal
public class NormalizedQueryFactory {

    /**
     * Creates a new Query execution tree for the provided query
     */
    public NormalizedQuery createNormalizedQuery(GraphQLSchema graphQLSchema, Document document, String operationName, Map<String, Object> variables) {

        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);

        FieldCollectorNormalizedQuery fieldCollector = new FieldCollectorNormalizedQuery();
        FieldCollectorNormalizedQueryParams parameters = FieldCollectorNormalizedQueryParams
                .newParameters()
                .fragments(getOperationResult.fragmentsByName)
                .schema(graphQLSchema)
                .variables(variables)
                .build();

        List<NormalizedQueryField> roots = fieldCollector.collectFromOperation(parameters, getOperationResult.operationDefinition, graphQLSchema.getQueryType());

        List<NormalizedQueryField> realRoots = new ArrayList<>();
        for (NormalizedQueryField root : roots) {
            realRoots.add(buildFieldWithChildren(root, fieldCollector, parameters));
        }

        return new NormalizedQuery(realRoots);
    }


    private NormalizedQueryField buildFieldWithChildren(NormalizedQueryField field, FieldCollectorNormalizedQuery fieldCollector, FieldCollectorNormalizedQueryParams fieldCollectorNormalizedQueryParams) {
        List<NormalizedQueryField> fieldsWithoutChildren = fieldCollector.collectFields(fieldCollectorNormalizedQueryParams, field);
        List<NormalizedQueryField> realChildren = new ArrayList<>();
        for (NormalizedQueryField fieldWithoutChildren : fieldsWithoutChildren) {
            NormalizedQueryField realChild = buildFieldWithChildren(fieldWithoutChildren, fieldCollector, fieldCollectorNormalizedQueryParams);
            realChildren.add(realChild);
        }
        return field.transform(builder -> builder.children(realChildren));
    }
}
