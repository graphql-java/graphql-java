package graphql.normalized;

import graphql.Assert;
import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.schema.GraphQLSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class FieldCollectorNormalizedQueryParams {
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final Map<String, Object> coercedVariableValues;
    private final Map<String, NormalizedInputValue> normalizedVariableValues;

    public List<PossibleMerger> possibleMergerList = new ArrayList<>();

    public static class PossibleMerger {
        ExecutableNormalizedField parent;
        String resultKey;

        public PossibleMerger(ExecutableNormalizedField parent, String resultKey) {
            this.parent = parent;
            this.resultKey = resultKey;
        }
    }

    public void addPossibleMergers(ExecutableNormalizedField parent, String resultKey) {
        possibleMergerList.add(new PossibleMerger(parent, resultKey));
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    @NotNull
    public Map<String, Object> getCoercedVariableValues() {
        return coercedVariableValues;
    }

    @Nullable
    public Map<String, NormalizedInputValue> getNormalizedVariableValues() {
        return normalizedVariableValues;
    }

    private FieldCollectorNormalizedQueryParams(GraphQLSchema graphQLSchema,
                                                Map<String, Object> coercedVariableValues,
                                                Map<String, NormalizedInputValue> normalizedVariableValues,
                                                Map<String, FragmentDefinition> fragmentsByName) {
        this.fragmentsByName = fragmentsByName;
        this.graphQLSchema = graphQLSchema;
        this.coercedVariableValues = coercedVariableValues;
        this.normalizedVariableValues = normalizedVariableValues;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private final Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        private final Map<String, Object> coercedVariableValues = new LinkedHashMap<>();
        private Map<String, NormalizedInputValue> normalizedVariableValues;

        /**
         * @see FieldCollectorNormalizedQueryParams#newParameters()
         */
        private Builder() {

        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
            return this;
        }

        public Builder fragments(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName.putAll(fragmentsByName);
            return this;
        }

        public Builder coercedVariables(Map<String, Object> coercedVariableValues) {
            this.coercedVariableValues.putAll(coercedVariableValues);
            return this;
        }

        public Builder normalizedVariables(Map<String, NormalizedInputValue> normalizedVariableValues) {
            this.normalizedVariableValues = normalizedVariableValues;
            return this;
        }

        public FieldCollectorNormalizedQueryParams build() {
            Assert.assertNotNull(graphQLSchema, () -> "You must provide a schema");
            return new FieldCollectorNormalizedQueryParams(graphQLSchema, coercedVariableValues, normalizedVariableValues, fragmentsByName);
        }

    }
}
