package graphql.execution;

import graphql.Internal;
import graphql.analysis.QueryTraversal;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FieldDirectives {

    private final Map<Field, Map<String, GraphQLDirective>> fieldDirectives;
    private final Map<Field, Map<String, GraphQLDirective>> inlineFragmentDirectives;
    private final Map<Field, Map<String, GraphQLDirective>> fragmentDirectives;
    private final Map<Field, Map<String, GraphQLDirective>> fragmentDefDirectives;
    private final Map<String, GraphQLDirective> operationDirectives;

    @Internal
    public FieldDirectives(Document document, GraphQLSchema schema, Map<String, FragmentDefinition> fragmentsByName, Map<String, Object> variables, OperationDefinition operationDefinition, DirectivesResolver directivesResolver) {
        DirectiveCollector collector = DirectiveCollector.collect(document, schema, fragmentsByName, variables, operationDefinition.getName(), directivesResolver);
        this.fieldDirectives = collector.getFieldDirectives();
        this.inlineFragmentDirectives = collector.getInlineFragmentDirectives();
        this.fragmentDirectives = collector.getFragmentDirectives();
        this.fragmentDefDirectives = collector.getFragmentDefDirectives();
        this.operationDirectives = directivesResolver.resolveDirectives(operationDefinition.getDirectives(), schema, variables);
    }

    public Map<String, GraphQLDirective> getFieldDirectives(Field field) {
        return fieldDirectives.get(field);
    }

    public Map<String, List<GraphQLDirective>> getFieldDirectives(List<Field> fields) {
        return getFieldDirectives(fieldDirectives, fields);
    }

    public Map<String, List<GraphQLDirective>> getInlineFragmentDirectives(List<Field> fields) {
        return getFieldDirectives(inlineFragmentDirectives, fields);
    }

    public Map<String, List<GraphQLDirective>> getFragmentDirectives(List<Field> fields) {
        return getFieldDirectives(fragmentDirectives, fields);
    }

    public Map<String, List<GraphQLDirective>> getFragmentDefDirectives(List<Field> fields) {
        return getFieldDirectives(fragmentDefDirectives, fields);
    }

    public Map<String, GraphQLDirective> getOperationDirectives() {
        return operationDirectives;
    }

    private Map<String, List<GraphQLDirective>> getFieldDirectives(Map<Field, Map<String, GraphQLDirective>> directivesMap, List<Field> fields) {
        return fields.stream()
                .flatMap(field -> directivesMap.getOrDefault(field, Collections.emptyMap()).values().stream())
                .collect(Collectors.groupingBy(GraphQLDirective::getName));
    }

    private static class DirectiveCollector extends QueryVisitorStub {

        private final GraphQLSchema schema;
        private final Map<String, Object> variables;
        private final DirectivesResolver directivesResolver;
        private final Map<Field, Map<String, GraphQLDirective>> fieldDirectives;
        private final Map<Field, Map<String, GraphQLDirective>> inlineFragmentDirectives;
        private final Map<Field, Map<String, GraphQLDirective>> fragmentDirectives;
        private final Map<Field, Map<String, GraphQLDirective>> fragmentDefDirectives;
        private final Map<Field, Set<Node>> fragmentsPerField;

        private DirectiveCollector(Document document, GraphQLSchema schema, Map<String, FragmentDefinition> fragmentsByName, Map<String, Object> variables, String operationName, DirectivesResolver directivesResolver) {
            this.schema = schema;
            this.variables = variables;
            this.directivesResolver = directivesResolver;
            this.fieldDirectives = new HashMap<>();
            this.inlineFragmentDirectives = new HashMap<>();
            this.fragmentDirectives = new HashMap<>();
            this.fragmentDefDirectives = new HashMap<>();
            this.fragmentsPerField = new HashMap<>();
        }

        static DirectiveCollector collect(Document document, GraphQLSchema schema, Map<String, FragmentDefinition> fragmentsByName, Map<String, Object> variables, String operationName, DirectivesResolver directivesResolver) {
            DirectiveCollector fragmentDirectiveCollector = new DirectiveCollector(document, schema, fragmentsByName, variables, operationName, directivesResolver);
            QueryTraversal traversal = QueryTraversal.newQueryTraversal()
                    .schema(schema)
                    .variables(variables)
                    .document(document)
                    .operationName(operationName)
                    .build();
            traversal.visitPostOrder(fragmentDirectiveCollector);
            return fragmentDirectiveCollector;
        }

        @Override
        public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment env) {
            env.getInlineFragment().getSelectionSet().getSelections().forEach(selection -> {
                if (selection instanceof Field) {
                    addFragmentDirectivesForField((Field) selection, env.getInlineFragment());
                } else {
                    fragmentsPerField.entrySet().stream()
                            .filter(entry -> entry.getValue().contains(selection))
                            .forEach(entry -> addFragmentDirectivesForField(entry.getKey(), env.getInlineFragment()));
                }
            });
        }

        @Override
        public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment env) {
            env.getFragmentDefinition().getSelectionSet().getSelections().forEach(selection -> {
                if (selection instanceof Field) {
                    addFragmentDirectivesForField((Field) selection, env.getFragmentSpread(), env.getFragmentDefinition());
                } else {
                    fragmentsPerField.entrySet().stream()
                            .filter(entry -> entry.getValue().contains(selection))
                            .forEach(entry -> addFragmentDirectivesForField(entry.getKey(), env.getFragmentSpread(), env.getFragmentDefinition()));
                }
            });
        }

        @Override
        public void visitField(QueryVisitorFieldEnvironment env) {
            fieldDirectives.put(env.getField(), getDirectives(env.getField().getDirectives()));
        }

        private Map<String, GraphQLDirective> getDirectives(List<Directive> directives) {
            return directivesResolver.resolveDirectives(directives, schema, variables);
        }

        @SuppressWarnings("Duplicates")
        private void addFragmentDirectivesForField(Field field, FragmentSpread frag, FragmentDefinition fragDef) {
            fragmentsPerField.computeIfAbsent(field, f -> new HashSet<>());
            fragmentsPerField.get(field).add(frag);
            fragmentDirectives.computeIfAbsent(field, f -> new HashMap<>());
            fragmentDirectives.get(field).putAll(getDirectives(frag.getDirectives()));
            fragmentDefDirectives.computeIfAbsent(field, f -> new HashMap<>());
            fragmentDefDirectives.get(field).putAll(getDirectives(fragDef.getDirectives()));
        }

        @SuppressWarnings("Duplicates")
        private void addFragmentDirectivesForField(Field field, InlineFragment frag) {
            fragmentsPerField.computeIfAbsent(field, f -> new HashSet<>());
            fragmentsPerField.get(field).add(frag);
            inlineFragmentDirectives.computeIfAbsent(field, f -> new HashMap<>());
            inlineFragmentDirectives.get(field).putAll(getDirectives(frag.getDirectives()));
        }

        public Map<Field, Map<String, GraphQLDirective>> getFieldDirectives() {
            return fieldDirectives;
        }

        public Map<Field, Map<String, GraphQLDirective>> getInlineFragmentDirectives() {
            return inlineFragmentDirectives;
        }

        public Map<Field, Map<String, GraphQLDirective>> getFragmentDirectives() {
            return fragmentDirectives;
        }

        public Map<Field, Map<String, GraphQLDirective>> getFragmentDefDirectives() {
            return fragmentDefDirectives;
        }
    }
}
