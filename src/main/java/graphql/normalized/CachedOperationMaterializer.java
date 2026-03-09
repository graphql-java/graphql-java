package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Materializes a {@link CachedNormalizedOperation} into an {@link ExecutableNormalizedOperation}
 * by evaluating inclusion conditions against concrete variable values, coercing arguments,
 * and merging fields.
 * <p>
 * This is the cheap per-request step. The expensive normalization work (schema traversal,
 * fragment resolution, type condition computation) has already been done by
 * {@link CachedNormalizedOperationFactory} and cached.
 */
@Internal
public class CachedOperationMaterializer {

    /**
     * Materialize a cached operation into an executable one using the provided variables.
     *
     * @param cachedOperation the cached, variable-independent operation
     * @param variables       the coerced variable values for this request
     * @param schema          the GraphQL schema
     * @param graphQLContext  the context for value coercion
     * @param locale          the locale for value coercion
     * @return a fully materialized ExecutableNormalizedOperation
     */
    public static ExecutableNormalizedOperation materialize(
            CachedNormalizedOperation cachedOperation,
            Map<String, Object> variables,
            GraphQLSchema schema,
            GraphQLContext graphQLContext,
            Locale locale
    ) {
        return new MaterializerImpl(cachedOperation, variables, schema, graphQLContext, locale).materialize();
    }

    private static class MaterializerImpl {
        private final CachedNormalizedOperation cachedOperation;
        private final Map<String, Object> variables;
        private final GraphQLSchema schema;
        private final GraphQLContext graphQLContext;
        private final Locale locale;

        private final List<ExecutableNormalizedField> rootEnfs = new ArrayList<>();
        private int fieldCount = 0;
        private int maxDepth = 0;

        // Tracks fields that may need merging (same result key under same parent)
        private final List<PossibleMerger> possibleMergers = new ArrayList<>();

        MaterializerImpl(CachedNormalizedOperation cachedOperation,
                         Map<String, Object> variables,
                         GraphQLSchema schema,
                         GraphQLContext graphQLContext,
                         Locale locale) {
            this.cachedOperation = cachedOperation;
            this.variables = variables;
            this.schema = schema;
            this.graphQLContext = graphQLContext;
            this.locale = locale;
        }

        ExecutableNormalizedOperation materialize() {
            // Phase 1: Walk the cached tree, evaluate conditions, create ENFs
            for (CachedNormalizedField cachedRoot : cachedOperation.getTopLevelFields()) {
                ExecutableNormalizedField enf = materializeField(cachedRoot, null);
                if (enf != null) {
                    rootEnfs.add(enf);
                }
            }

            // Phase 2: Merge fields with same result key under the same parent
            for (PossibleMerger merger : possibleMergers) {
                List<ExecutableNormalizedField> childrenWithSameResultKey =
                        merger.parent.getChildrenWithSameResultKey(merger.resultKey);
                if (childrenWithSameResultKey.size() > 1) {
                    ENFMerger.merge(merger.parent, childrenWithSameResultKey, schema, false);
                }
            }

            // Build the operation
            // For this prototype, we don't populate the full fieldToNormalizedField/mergedField/queryDirectives
            // maps. Those are secondary indexes used by the execution engine and would need the original
            // AST Field references. The core tree structure is correct.
            return new ExecutableNormalizedOperation(
                    cachedOperation.getOperation(),
                    cachedOperation.getOperationName(),
                    rootEnfs,
                    ImmutableListMultimap.of(),  // fieldToNormalizedField - not populated in prototype
                    ImmutableMap.of(),            // normalizedFieldToMergedField - not populated in prototype
                    ImmutableMap.of(),            // normalizedFieldToQueryDirectives - not populated in prototype
                    ImmutableListMultimap.of(),  // coordinatesToNormalizedFields - not populated in prototype
                    fieldCount,
                    maxDepth
            );
        }

        private ExecutableNormalizedField materializeField(CachedNormalizedField cached, ExecutableNormalizedField enfParent) {
            // Step 1: Evaluate inclusion condition
            if (!cached.getInclusionCondition().evaluate(variables)) {
                return null; // Field excluded for these variables
            }

            fieldCount++;
            if (cached.getLevel() > maxDepth) {
                maxDepth = cached.getLevel();
            }

            // Step 2: Coerce arguments
            Set<String> objectTypeNames = cached.getObjectTypeNames();
            String firstObjectType = objectTypeNames.iterator().next();
            GraphQLObjectType type = (GraphQLObjectType) schema.getType(firstObjectType);
            GraphQLFieldDefinition fieldDef = Introspection.getFieldDefinition(schema, type, cached.getFieldName());

            Map<String, Object> resolvedArgs = ValuesResolver.getArgumentValues(
                    fieldDef.getArguments(),
                    cached.getAstArguments(),
                    CoercedVariables.of(variables),
                    graphQLContext,
                    locale
            );

            // Step 3: Build the ENF
            ExecutableNormalizedField enf = ExecutableNormalizedField.newNormalizedField()
                    .fieldName(cached.getFieldName())
                    .alias(cached.getAlias())
                    .objectTypeNames(ImmutableList.copyOf(objectTypeNames))
                    .astArguments(cached.getAstArguments())
                    .resolvedArguments(resolvedArgs)
                    .level(cached.getLevel())
                    .parent(enfParent)
                    .build();

            // Step 4: Materialize children
            Map<String, Integer> resultKeyCount = new LinkedHashMap<>();
            for (CachedNormalizedField cachedChild : cached.getChildren()) {
                ExecutableNormalizedField childEnf = materializeField(cachedChild, enf);
                if (childEnf != null) {
                    enf.addChild(childEnf);
                    String rk = childEnf.getResultKey();
                    int count = resultKeyCount.merge(rk, 1, Integer::sum);
                    if (count == 2) {
                        // Two children with the same result key — need potential merge
                        possibleMergers.add(new PossibleMerger(enf, rk));
                    }
                }
            }

            return enf;
        }

        private static class PossibleMerger {
            final ExecutableNormalizedField parent;
            final String resultKey;

            PossibleMerger(ExecutableNormalizedField parent, String resultKey) {
                this.parent = parent;
                this.resultKey = resultKey;
            }
        }
    }
}
