package graphql.analysis;

import graphql.Internal;
import graphql.language.Argument;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLSchema;
import graphql.validation.DocumentVisitor;
import graphql.validation.LanguageTraversal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static graphql.Assert.assertTrue;
import static java.util.Collections.emptySet;

@Internal
public class OperationDependencyChecker {
    private final GraphQLSchema graphQLSchema;
    private final Map<String, Object> variables;
    private final Map<String, Set<String>> exportedVariables;
    private final Map<String, Set<String>> consumedVariables;
    private final Document document;

    public OperationDependencyChecker(GraphQLSchema graphQLSchema, Document document, Map<String, Object> variables) {
        this.graphQLSchema = graphQLSchema;
        this.document = document;
        this.variables = variables;
        this.exportedVariables = new HashMap<>();
        this.consumedVariables = new HashMap<>();
        discoverVariables();
    }

    private void discoverVariables() {

        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                discoverVariablesForOperation(operationDefinition.getName(), operationDefinition);

            }
        }
    }

    private void discoverVariablesForOperation(String operationName, Node startNode) {
        String keyName = operationName == null ? "implicit" : operationName;
        QueryTraversal queryTraversal = new QueryTraversal(graphQLSchema, document, operationName, variables);
        queryTraversal.visitPreOrder(env -> {
            Field field = env.getField();
            Optional<String> exportAsName = getExportAsName(field);
            if (exportAsName.isPresent()) {
                Set<String> vars = exportedVariables.getOrDefault(keyName, new HashSet<>());
                vars.add(exportAsName.get());
                exportedVariables.put(keyName, vars);
            }
        });

        LanguageTraversal languageTraversal = new LanguageTraversal();
        languageTraversal.traverse(startNode, new DocumentVisitor() {
            @Override
            public void enter(Node node, List<Node> path) {
                if (node instanceof VariableDefinition) {
                    VariableDefinition variableDef = (VariableDefinition) node;
                    Set<String> varsDefined = consumedVariables.getOrDefault(keyName, new HashSet<>());
                    varsDefined.add(variableDef.getName());
                    consumedVariables.put(keyName, varsDefined);
                }
            }

            @Override
            public void leave(Node node, List<Node> path) {
            }
        });
    }

    private Optional<String> getExportAsName(Field field) {
        Directive exportDirective = field.getDirective("export");
        if (exportDirective == null) {
            return Optional.empty();
        }
        Argument as = exportDirective.getArgument("as");
        if (as == null) {
            return Optional.empty();
        }
        Value value = as.getValue();
        assertTrue(value instanceof StringValue, "The export directive MUST return a string value");

        final String exportAsName = ((StringValue) value).getValue();
        return Optional.of(exportAsName);
    }

    public boolean currentIsDependentOnPrevious(String currentOperationName, String previousOperationName) {
        Set<String> previousExportedVariables = exportedVariables.getOrDefault(previousOperationName, emptySet());
        Set<String> currentVariables = consumedVariables.getOrDefault(currentOperationName, emptySet());
        for (String exportedVariable : previousExportedVariables) {
            if (currentVariables.contains(exportedVariable)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Set<String>> getExportedVariables() {
        return exportedVariables;
    }

    public Map<String, Set<String>> getConsumedVariables() {
        return consumedVariables;
    }
}
