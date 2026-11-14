package graphql.execution.directives;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.MergedField;
import graphql.execution.NormalizedVariables;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.normalized.NormalizedInputValue;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.util.FpKit;
import graphql.util.LockKit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;

/**
 * These objects are ALWAYS in the context of a single MergedField
 * <p>
 * Also note we compute these values lazily
 */
@Internal
public class QueryDirectivesImpl implements QueryDirectives {

    private final DirectivesResolver directivesResolver = new DirectivesResolver();
    private final MergedField mergedField;
    private final GraphQLSchema schema;
    private final CoercedVariables coercedVariables;
    private final Supplier<NormalizedVariables> normalizedVariableValues;
    private final GraphQLContext graphQLContext;
    private final Locale locale;

    private final LockKit.ComputedOnce computedOnce = new LockKit.ComputedOnce();
    private volatile ImmutableMap<Field, List<GraphQLDirective>> fieldDirectivesByField;
    private volatile ImmutableMap<String, List<GraphQLDirective>> fieldDirectivesByName;
    private volatile ImmutableMap<Field, List<QueryAppliedDirective>> fieldAppliedDirectivesByField;
    private volatile ImmutableMap<String, List<QueryAppliedDirective>> fieldAppliedDirectivesByName;
    private volatile ImmutableMap<QueryAppliedDirective, Directive> astDirectivesByAppliedDirective;
    private final Supplier<Map<QueryAppliedDirective, Map<String, NormalizedInputValue>>> normalizedValuesByAppliedDirective =
            FpKit.interThreadMemoize(this::computeNormalizedValues);

    public QueryDirectivesImpl(MergedField mergedField, GraphQLSchema schema, CoercedVariables coercedVariables, Supplier<NormalizedVariables> normalizedVariableValues, GraphQLContext graphQLContext, Locale locale) {
        this.mergedField = assertNotNull(mergedField);
        this.schema = assertNotNull(schema);
        this.coercedVariables = assertNotNull(coercedVariables);
        this.normalizedVariableValues = assertNotNull(normalizedVariableValues);
        this.graphQLContext = assertNotNull(graphQLContext);
        this.locale = assertNotNull(locale);
    }

    private void computeValuesLazily() {
        computedOnce.runOnce(this::computeDirectiveValues);
    }

    private void computeDirectiveValues() {
        Map<Field, List<GraphQLDirective>> byField = new LinkedHashMap<>();
        Map<Field, List<QueryAppliedDirective>> byFieldApplied = new LinkedHashMap<>();
        Map<QueryAppliedDirective, Directive> astDirectives = new LinkedHashMap<>();

        mergedField.forEach(field -> {
            BiMap<GraphQLDirective, Directive> directivesMap = directivesResolver
                    .resolveDirectives(field.getDirectives(), schema, coercedVariables, graphQLContext, locale);
            byField.put(field, ImmutableList.copyOf(directivesMap.keySet()));

            ImmutableList.Builder<QueryAppliedDirective> appliedDirectiveBuilder = ImmutableList.builder();
            directivesMap.forEach((resolvedDirective, astDirective) -> {
                QueryAppliedDirective appliedDirective = directivesResolver.toAppliedDirective(resolvedDirective);
                appliedDirectiveBuilder.add(appliedDirective);
                astDirectives.put(appliedDirective, astDirective);
            });
            byFieldApplied.put(field, appliedDirectiveBuilder.build());
        });

        Map<String, List<GraphQLDirective>> byName = new LinkedHashMap<>();
        for (List<GraphQLDirective> directives : byField.values()) {
            for (GraphQLDirective directive : directives) {
                byName.computeIfAbsent(directive.getName(), FpKit.newList()).add(directive);
            }
        }
        Map<String, List<QueryAppliedDirective>> byNameApplied = new LinkedHashMap<>();
        for (List<QueryAppliedDirective> directives : byFieldApplied.values()) {
            for (QueryAppliedDirective directive : directives) {
                byNameApplied.computeIfAbsent(directive.getName(), FpKit.newList()).add(directive);
            }
        }

        this.fieldDirectivesByName = ImmutableMap.copyOf(byName);
        this.fieldDirectivesByField = ImmutableMap.copyOf(byField);
        this.fieldAppliedDirectivesByName = ImmutableMap.copyOf(byNameApplied);
        this.fieldAppliedDirectivesByField = ImmutableMap.copyOf(byFieldApplied);
        this.astDirectivesByAppliedDirective = ImmutableMap.copyOf(astDirectives);
    }

    private Map<QueryAppliedDirective, Map<String, NormalizedInputValue>> computeNormalizedValues() {
        computeValuesLazily();
        if (astDirectivesByAppliedDirective.isEmpty()) {
            return emptyMap();
        }
        NormalizedVariables normalizedVariables = normalizedVariableValues.get();
        if (normalizedVariables == null) {
            return emptyMap();
        }

        Map<QueryAppliedDirective, Map<String, NormalizedInputValue>> result = new LinkedHashMap<>();
        Map<String, NormalizedInputValue> variables = normalizedVariables.toMap();
        fieldAppliedDirectivesByName.values().forEach(directives -> directives.forEach(directive ->
                result.put(directive, normalizeDirectiveArguments(directive, variables))));
        return ImmutableMap.copyOf(result);
    }

    private Map<String, NormalizedInputValue> normalizeDirectiveArguments(QueryAppliedDirective directive,
                                                                        Map<String, NormalizedInputValue> variables) {
        Directive astDirective = assertNotNull(astDirectivesByAppliedDirective.get(directive));
        GraphQLDirective definition = assertNotNull(schema.getDirective(directive.getName()));
        return ValuesResolver.getNormalizedArgumentValues(definition.getArguments(), astDirective.getArguments(), variables);
    }


    @Override
    public Map<Field, List<GraphQLDirective>> getImmediateDirectivesByField() {
        computeValuesLazily();
        return fieldDirectivesByField;
    }

    @Override
    public Map<Field, List<QueryAppliedDirective>> getImmediateAppliedDirectivesByField() {
        computeValuesLazily();
        return fieldAppliedDirectivesByField;
    }

    @Override
    public Map<QueryAppliedDirective, Map<String, NormalizedInputValue>> getNormalizedInputValueByImmediateAppliedDirectives() {
        return normalizedValuesByAppliedDirective.get();
    }

    @Override
    public Map<String, List<GraphQLDirective>> getImmediateDirectivesByName() {
        computeValuesLazily();
        return fieldDirectivesByName;
    }

    @Override
    public Map<String, List<QueryAppliedDirective>> getImmediateAppliedDirectivesByName() {
        computeValuesLazily();
        return fieldAppliedDirectivesByName;
    }

    @Override
    public List<GraphQLDirective> getImmediateDirective(String directiveName) {
        computeValuesLazily();
        return getImmediateDirectivesByName().getOrDefault(directiveName, emptyList());
    }

    @Override
    public List<QueryAppliedDirective> getImmediateAppliedDirective(String directiveName) {
        computeValuesLazily();
        return getImmediateAppliedDirectivesByName().getOrDefault(directiveName, emptyList());
    }
}
