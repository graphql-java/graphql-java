package graphql.execution.directives;

import graphql.Internal;
import graphql.analysis.QueryTraversal;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.execution.MergedField;
import graphql.language.Directive;
import graphql.language.DirectivesContainer;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * This is responsible for building a chain of directives for each field in the query.  This is designed to be called
 * once at the start of the query and hence only one traversal of the tree is needed for a particular query
 */
@Internal
public class FieldDirectiveCollector {

    private final DirectivesResolver directivesResolver = new DirectivesResolver();

    public List<QueryDirectivesInfo> combineDirectivesForField(MergedField mergedField, Map<Field, List<QueryDirectivesInfo>> allDirectives) {
        List<QueryDirectivesInfo> directives = new ArrayList<>();
        for (Field field : mergedField.getFields()) {
            List<QueryDirectivesInfo> fieldDirections = allDirectives.getOrDefault(field, emptyList());
            directives.addAll(fieldDirections);
        }
        Collections.sort(directives);
        return directives;
    }

    public Map<Field, List<QueryDirectivesInfo>> collectDirectivesForAllFields(Document document, GraphQLSchema schema, Map<String, Object> variables, OperationDefinition operationDefinition) {
        Map<Field, List<QueryDirectivesInfo>> fieldDirectives = new LinkedHashMap<>();
        QueryTraversal traversal = QueryTraversal.newQueryTraversal()
                .schema(schema)
                .variables(variables)
                .document(document)
                .operationName(operationDefinition.getName())
                .build();
        traversal.visitPostOrder(new QueryVisitorStub() {
            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                Field field = env.getField();
                List<Node> parentNodes = env.getTraverserContext().getParentNodes();
                List<QueryDirectivesInfo> directivesInfos = walkDirectivesFromField(field, parentNodes, schema, variables);
                if (!directivesInfos.isEmpty()) {
                    // don't waste memory with empty lists
                    fieldDirectives.put(field, directivesInfos);
                }
            }
        });
        return fieldDirectives;
    }

    /*
      This code heavily relies on the behaviour that the parent nodes of a field lead upwards to the OperationDefinition
      and includes fragments and fragment spreads etc.. that lead to that field
     */
    private List<QueryDirectivesInfo> walkDirectivesFromField(Field field, List<Node> parentNodes, GraphQLSchema schema, Map<String, Object> variables) {
        int distance = 0;
        List<QueryDirectivesInfo> fieldDirectivePositions = new ArrayList<>();
        List<Directive> astDirectives = field.getDirectives();
        if (!astDirectives.isEmpty()) {
            fieldDirectivePositions.add(new QueryDirectivesInfoImpl(field, distance, mkDirectives(astDirectives, schema, variables)));
        }
        for (Node parentNode : parentNodes) {
            if (parentNode instanceof DirectivesContainer) {
                distance++;
                DirectivesContainer<?> parentContainer = (DirectivesContainer) parentNode;
                astDirectives = parentContainer.getDirectives();
                if (!astDirectives.isEmpty()) {
                    fieldDirectivePositions.add(new QueryDirectivesInfoImpl(parentContainer, distance, mkDirectives(astDirectives, schema, variables)));
                }
            }
        }
        return fieldDirectivePositions;
    }

    private Map<String, GraphQLDirective> mkDirectives(List<Directive> directives, GraphQLSchema schema, Map<String, Object> variables) {
        return directivesResolver.resolveDirectives(directives, schema, variables);
    }

}
