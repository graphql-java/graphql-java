package graphql.validation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import graphql.Assert;
import graphql.Directives;
import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.TypeFromAST;
import graphql.execution.ValuesResolver;
import graphql.i18n.I18nMsg;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.AstComparator;
import graphql.language.BooleanValue;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeUtil;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.SourceLocation;
import graphql.language.StringValue;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.schema.InputValueWithState;
import graphql.util.StringKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static graphql.collect.ImmutableKit.addToList;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.schema.GraphQLTypeUtil.isEnum;
import static graphql.schema.GraphQLTypeUtil.isInput;
import static graphql.schema.GraphQLTypeUtil.isLeaf;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.isNotWrapped;
import static graphql.schema.GraphQLTypeUtil.isNullable;
import static graphql.schema.GraphQLTypeUtil.isScalar;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

import static graphql.validation.ValidationError.newValidationError;
import static graphql.validation.ValidationErrorType.BadValueForDefaultArg;
import static graphql.validation.ValidationErrorType.DuplicateArgumentNames;
import static graphql.validation.ValidationErrorType.DuplicateDirectiveName;
import static graphql.validation.ValidationErrorType.DuplicateFragmentName;
import static graphql.validation.ValidationErrorType.DuplicateIncrementalLabel;
import static graphql.validation.ValidationErrorType.DuplicateOperationName;
import static graphql.validation.ValidationErrorType.DuplicateVariableName;
import static graphql.validation.ValidationErrorType.FieldUndefined;
import static graphql.validation.ValidationErrorType.FieldsConflict;
import static graphql.validation.ValidationErrorType.FragmentCycle;
import static graphql.validation.ValidationErrorType.FragmentTypeConditionInvalid;
import static graphql.validation.ValidationErrorType.InlineFragmentTypeConditionInvalid;
import static graphql.validation.ValidationErrorType.InvalidFragmentType;
import static graphql.validation.ValidationErrorType.LoneAnonymousOperationViolation;
import static graphql.validation.ValidationErrorType.MisplacedDirective;
import static graphql.validation.ValidationErrorType.MissingDirectiveArgument;
import static graphql.validation.ValidationErrorType.MissingFieldArgument;
import static graphql.validation.ValidationErrorType.NonExecutableDefinition;
import static graphql.validation.ValidationErrorType.NonInputTypeOnVariable;
import static graphql.validation.ValidationErrorType.NullValueForNonNullArgument;
import static graphql.validation.ValidationErrorType.SubselectionNotAllowed;
import static graphql.validation.ValidationErrorType.SubselectionRequired;
import static graphql.validation.ValidationErrorType.SubscriptionIntrospectionRootField;
import static graphql.validation.ValidationErrorType.SubscriptionMultipleRootFields;
import static graphql.validation.ValidationErrorType.UndefinedFragment;
import static graphql.validation.ValidationErrorType.UndefinedVariable;
import static graphql.validation.ValidationErrorType.UnknownArgument;
import static graphql.validation.ValidationErrorType.UnknownDirective;
import static graphql.validation.ValidationErrorType.UnknownOperation;
import static graphql.validation.ValidationErrorType.UnknownType;
import static graphql.validation.ValidationErrorType.UnusedFragment;
import static graphql.validation.ValidationErrorType.UnusedVariable;
import static graphql.validation.ValidationErrorType.VariableTypeMismatch;
import static graphql.validation.ValidationErrorType.WrongType;
import static java.lang.System.arraycopy;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;
import static graphql.validation.ValidationErrorType.UniqueObjectFieldName;

/**
 * Consolidated operation validator that implements all GraphQL validation rules
 * from the specification. Replaces the former 31 separate rule classes and the
 * RulesVisitor dispatch layer.
 *
 * <h2>Traversal Model</h2>
 *
 * <p>This validator tracks two independent state variables during traversal:
 *
 * <ul>
 *   <li><b>{@code fragmentRetraversalDepth}</b> - Tracks whether we are in the primary document
 *       traversal ({@code == 0}) or inside a manual re-traversal of a fragment via a spread
 *       ({@code > 0}).</li>
 *   <li><b>{@code operationScope}</b> - Tracks whether we are currently inside an operation
 *       definition ({@code true}) or outside of any operation ({@code false}).</li>
 * </ul>
 *
 * <h2>Traversal States</h2>
 *
 * <p>These two variables create four possible states, but only three actually occur:
 *
 * <pre>
 * ┌────────────────────────────────────────┬────────────────────────────────────────────────┐
 * │ State                                  │ Description                                    │
 * ├────────────────────────────────────────┼────────────────────────────────────────────────┤
 * │ depth=0, operationScope=false          │ PRIMARY TRAVERSAL, OUTSIDE OPERATION           │
 * │                                        │ Visiting document root or fragment definitions │
 * │                                        │ after all operations have been processed.      │
 * │                                        │ Example: FragmentDefinition at document level  │
 * ├────────────────────────────────────────┼────────────────────────────────────────────────┤
 * │ depth=0, operationScope=true           │ PRIMARY TRAVERSAL, INSIDE OPERATION            │
 * │                                        │ Visiting nodes directly within an operation.   │
 * │                                        │ Example: Field, InlineFragment in operation    │
 * ├────────────────────────────────────────┼────────────────────────────────────────────────┤
 * │ depth>0, operationScope=true           │ FRAGMENT RETRAVERSAL, INSIDE OPERATION         │
 * │                                        │ Manually traversing into a fragment via spread.│
 * │                                        │ Example: Nodes reached via ...FragmentName     │
 * ├────────────────────────────────────────┼────────────────────────────────────────────────┤
 * │ depth>0, operationScope=false          │ NEVER OCCURS                                   │
 * │                                        │ Retraversal only happens within an operation.  │
 * └────────────────────────────────────────┴────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Rule Categories</h2>
 *
 * <p>Rules are categorized by which states they should run in:
 *
 * <pre>
 * ┌──────────────────────┬──────────────────────┬─────────────────────┬─────────────────────┐
 * │ Rule Category        │ depth=0              │ depth=0             │ depth>0             │
 * │                      │ operationScope=false │ operationScope=true │ operationScope=true │
 * ├──────────────────────┼──────────────────────┼─────────────────────┼─────────────────────┤
 * │ Document-Level Rules │         RUN          │        RUN          │        SKIP         │
 * ├──────────────────────┼──────────────────────┼─────────────────────┼─────────────────────┤
 * │ Operation-Scoped     │        SKIP          │        RUN          │        RUN          │
 * │ Rules                │                      │                     │                     │
 * └──────────────────────┴──────────────────────┴─────────────────────┴─────────────────────┘
 * </pre>
 *
 * <h3>Document-Level Rules</h3>
 * <p>Check: {@code fragmentRetraversalDepth == 0} (via {@link #shouldRunDocumentLevelRules()})
 * <p>Purpose: Validate each AST node exactly once. Skip during fragment retraversal to avoid
 * duplicate errors (the fragment was already validated at document level).
 * <p>Examples: {@code FieldsOnCorrectType}, {@code UniqueFragmentNames}, {@code ScalarLeaves}
 *
 * <h3>Operation-Scoped Rules</h3>
 * <p>Check: {@code operationScope == true} (via {@link #shouldRunOperationScopedRules()})
 * <p>Purpose: Track state across an entire operation, including all fragments it references.
 * These rules need to "follow" fragment spreads to see variable usages, defer directives, etc.
 * <p>Examples: {@code NoUndefinedVariables}, {@code NoUnusedVariables}, {@code VariableTypesMatch}
 *
 * <h2>Traversal Example</h2>
 *
 * <p>Consider this GraphQL document:
 * <pre>{@code
 * query GetUser($id: ID!) {
 *   user(id: $id) {
 *     ...UserFields
 *   }
 * }
 *
 * fragment UserFields on User {
 *   name
 *   friends {
 *     ...UserFields   # recursive spread
 *   }
 * }
 * }</pre>
 *
 * <p>The traversal proceeds as follows:
 *
 * <pre>
 * STEP  NODE                        depth  operationScope  DOC-LEVEL  OP-SCOPED
 * ────  ──────────────────────────  ─────  ──────────────  ─────────  ─────────
 *  1    Document                      0        false          RUN       SKIP
 *  2    OperationDefinition           0        true           RUN       RUN
 *  3    ├─ VariableDefinition $id     0        true           RUN       RUN
 *  4    ├─ Field "user"               0        true           RUN       RUN
 *  5    │  └─ FragmentSpread          0        true           RUN       RUN
 *       │     ...UserFields
 *       │     ┌─────────────────────────────────────────────────────────────┐
 *       │     │ MANUAL RETRAVERSAL INTO FRAGMENT                            │
 *       │     └─────────────────────────────────────────────────────────────┘
 *  6    │     FragmentDefinition      1        true          SKIP       RUN
 *  7    │     ├─ Field "name"         1        true          SKIP       RUN
 *  8    │     ├─ Field "friends"      1        true          SKIP       RUN
 *  9    │     │  └─ FragmentSpread    1        true          SKIP       RUN
 *       │     │     ...UserFields
 *       │     │     (already visited - skip to avoid infinite loop)
 *       │     └─────────────────────────────────────────────────────────────┘
 * 10    └─ (leave OperationDef)       0        false      [finalize op-scoped rules]
 * 11    FragmentDefinition            0        false          RUN       SKIP
 *       "UserFields" (at doc level)
 * 12    ├─ Field "name"               0        false          RUN       SKIP
 * 13    ├─ Field "friends"            0        false          RUN       SKIP
 * 14    │  └─ FragmentSpread          0        false          RUN       SKIP
 * </pre>
 *
 * <h2>Key Observations</h2>
 *
 * <ul>
 *   <li><b>Steps 6-9:</b> During retraversal, document-level rules SKIP because the fragment
 *       will be validated at steps 11-14. This prevents duplicate "field not found" errors.</li>
 *   <li><b>Steps 6-9:</b> Operation-scoped rules RUN to track that variables used inside
 *       {@code UserFields} are defined in the operation.</li>
 *   <li><b>Steps 11-14:</b> Operation-scoped rules SKIP because there's no operation context
 *       to track variables against.</li>
 *   <li><b>Step 9:</b> Recursive fragment spreads are tracked via {@code visitedFragmentSpreads}
 *       to prevent infinite loops during retraversal.</li>
 * </ul>
 *
 * @see OperationValidationRule
 */
@Internal
@NullMarked
@SuppressWarnings("rawtypes")
public class OperationValidator implements DocumentVisitor {

    // --- Infrastructure ---
    private final ValidationContext validationContext;
    private final ValidationErrorCollector errorCollector;
    private final ValidationUtil validationUtil;
    private final Predicate<OperationValidationRule> rulePredicate;

    // --- Traversal context ---
    /**
     * True when currently processing within an operation definition.
     */
    private boolean operationScope = false;
    /**
     * Depth of manual fragment traversal; 0 means primary document traversal.
     */
    private int fragmentRetraversalDepth = 0;
    /**
     * Tracks which fragments have been traversed via spreads to avoid infinite loops.
     */
    private final Set<String> visitedFragmentSpreads = new HashSet<>();

    // --- State: NoFragmentCycles ---
    private final Map<String, Set<String>> fragmentSpreadsMap = new HashMap<>();

    // --- State: NoUnusedFragments ---
    private final List<FragmentDefinition> allDeclaredFragments = new ArrayList<>();
    private List<String> unusedFragTracking_usedFragments = new ArrayList<>();
    private final Map<String, List<String>> spreadsInDefinition = new LinkedHashMap<>();
    private final List<List<String>> fragmentsUsedDirectlyInOperation = new ArrayList<>();

    // --- State: NoUndefinedVariables ---
    private final Set<String> definedVariableNames = new LinkedHashSet<>();

    // --- State: NoUnusedVariables ---
    private final List<VariableDefinition> unusedVars_variableDefinitions = new ArrayList<>();
    private final Set<String> unusedVars_usedVariables = new LinkedHashSet<>();

    // --- State: VariableTypesMatch ---
    private final VariablesTypesMatcher variablesTypesMatcher = new VariablesTypesMatcher();
    private @Nullable Map<String, VariableDefinition> variableDefinitionMap;

    // --- State: OverlappingFieldsCanBeMerged ---
    private final Set<Set<FieldAndType>> sameResponseShapeChecked = new LinkedHashSet<>();
    private final Set<Set<FieldAndType>> sameForCommonParentsChecked = new LinkedHashSet<>();
    private final Set<Set<Field>> conflictsReported = new LinkedHashSet<>();

    // --- State: LoneAnonymousOperation ---
    private boolean hasAnonymousOp = false;
    private int loneAnon_count = 0;

    // --- State: UniqueOperationNames ---
    private final Set<String> operationNames = new LinkedHashSet<>();

    // --- State: UniqueFragmentNames ---
    private final Set<String> fragmentNames = new LinkedHashSet<>();

    // --- State: DeferDirectiveLabel ---
    private final Set<String> checkedDeferLabels = new LinkedHashSet<>();

    // --- State: SubscriptionUniqueRootField ---
    private final FieldCollector fieldCollector = new FieldCollector();

    // --- State: Query Complexity Limits ---
    private int fieldCount = 0;
    private int currentFieldDepth = 0;
    private int maxFieldDepthSeen = 0;
    private final QueryComplexityLimits complexityLimits;
    // Fragment complexity calculated lazily during first spread
    private final Map<String, FragmentComplexityInfo> fragmentComplexityMap = new HashMap<>();
    // Max depth seen during current fragment traversal (for calculating fragment's internal depth)
    private int fragmentTraversalMaxDepth = 0;

    // --- State: Good Faith Introspection ---
    private final Map<String, Integer> introspectionFieldCounts = new HashMap<>();

    // --- Track whether we're in a context where fragment spread rules should run ---
    // fragmentRetraversalDepth == 0 means we're NOT inside a manually-traversed fragment => run non-fragment-spread checks
    // operationScope means we're inside an operation => can trigger fragment traversal

    private final boolean allRulesEnabled;

    public OperationValidator(ValidationContext validationContext, ValidationErrorCollector errorCollector, Predicate<OperationValidationRule> rulePredicate) {
        this.validationContext = validationContext;
        this.errorCollector = errorCollector;
        this.validationUtil = new ValidationUtil();
        this.rulePredicate = rulePredicate;
        this.complexityLimits = validationContext.getQueryComplexityLimits();
        this.allRulesEnabled = detectAllRulesEnabled(rulePredicate);
        prepareFragmentSpreadsMap();
    }

    private static boolean detectAllRulesEnabled(Predicate<OperationValidationRule> predicate) {
        for (OperationValidationRule rule : OperationValidationRule.values()) {
            if (!predicate.test(rule)) {
                return false;
            }
        }
        return true;
    }

    private boolean isRuleEnabled(OperationValidationRule rule) {
        return allRulesEnabled || rulePredicate.test(rule);
    }

    // ==================== Query Complexity Limit Helpers ====================

    private void checkFieldCountLimit() {
        if (fieldCount > complexityLimits.getMaxFieldsCount()) {
            throw new QueryComplexityLimitsExceeded(
                    ValidationErrorType.MaxQueryFieldsExceeded,
                    complexityLimits.getMaxFieldsCount(),
                    fieldCount);
        }
    }

    private void checkDepthLimit(int depth) {
        if (depth > maxFieldDepthSeen) {
            maxFieldDepthSeen = depth;
            if (maxFieldDepthSeen > complexityLimits.getMaxDepth()) {
                throw new QueryComplexityLimitsExceeded(
                        ValidationErrorType.MaxQueryDepthExceeded,
                        complexityLimits.getMaxDepth(),
                        maxFieldDepthSeen);
            }
        }
    }

    /**
     * Returns true when document-level rules should run.
     *
     * <p>Document-level rules validate each AST node exactly once during the primary
     * document traversal. They do NOT re-run when fragments are traversed through
     * spreads, which prevents duplicate validation errors.
     *
     * <p>Examples: {@code FieldsOnCorrectType}, {@code UniqueFragmentNames},
     * {@code ScalarLeaves}, {@code KnownDirectives}.
     *
     * @return true if {@code fragmentRetraversalDepth == 0} (primary traversal)
     */
    private boolean shouldRunDocumentLevelRules() {
        return fragmentRetraversalDepth == 0;
    }

    /**
     * Returns true when operation-scoped rules should run.
     *
     * <p>Operation-scoped rules must follow fragment spreads to see the complete
     * picture of an operation. They track state across all code paths, including
     * fragments referenced by the operation.
     *
     * <p>Examples: {@code NoUndefinedVariables}, {@code NoUnusedVariables},
     * {@code VariableTypesMatch}, {@code DeferDirectiveOnRootLevel}.
     *
     * @return true if currently processing within an operation scope
     */
    private boolean shouldRunOperationScopedRules() {
        return operationScope;
    }

    @Override
    public void enter(Node node, List<Node> ancestors) {
        validationContext.getTraversalContext().enter(node, ancestors);

        if (node instanceof Document) {
            checkDocument((Document) node);
        } else if (node instanceof Argument) {
            checkArgument((Argument) node);
        } else if (node instanceof TypeName) {
            checkTypeName((TypeName) node);
        } else if (node instanceof VariableDefinition) {
            checkVariableDefinition((VariableDefinition) node);
        } else if (node instanceof Field) {
            // Track complexity only during operation scope
            if (shouldRunOperationScopedRules()) {
                fieldCount++;
                currentFieldDepth++;
                checkFieldCountLimit();
                checkDepthLimit(currentFieldDepth);
                // Track max depth during fragment traversal for storing later
                if (fragmentRetraversalDepth > 0 && currentFieldDepth > fragmentTraversalMaxDepth) {
                    fragmentTraversalMaxDepth = currentFieldDepth;
                }
            }
            checkField((Field) node);
        } else if (node instanceof InlineFragment) {
            checkInlineFragment((InlineFragment) node);
        } else if (node instanceof Directive) {
            checkDirective((Directive) node, ancestors);
        } else if (node instanceof FragmentSpread) {
            checkFragmentSpread((FragmentSpread) node, ancestors);
        } else if (node instanceof FragmentDefinition) {
            checkFragmentDefinition((FragmentDefinition) node);
        } else if (node instanceof OperationDefinition) {
            checkOperationDefinition((OperationDefinition) node);
        } else if (node instanceof VariableReference) {
            checkVariable((VariableReference) node);
        } else if (node instanceof SelectionSet) {
            checkSelectionSet();
        } else if (node instanceof ObjectValue) {
            checkObjectValue((ObjectValue) node);
        }
    }

    @Override
    public void leave(Node node, List<Node> ancestors) {
        validationContext.getTraversalContext().leave(node, ancestors);

        if (node instanceof Document) {
            documentFinished();
        } else if (node instanceof OperationDefinition) {
            leaveOperationDefinition();
        } else if (node instanceof SelectionSet) {
            leaveSelectionSet();
        } else if (node instanceof FragmentDefinition) {
            leaveFragmentDefinition();
        } else if (node instanceof Field) {
            if (shouldRunOperationScopedRules()) {
                currentFieldDepth--;
            }
        }
    }

    private void addError(ValidationErrorType validationErrorType, Collection<? extends Node<?>> locations, String description) {
        List<SourceLocation> locationList = new ArrayList<>();
        for (Node<?> node : locations) {
            SourceLocation sourceLocation = node.getSourceLocation();
            if (sourceLocation != null) {
                locationList.add(sourceLocation);
            }
        }
        addError(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(locationList)
                .description(description));
    }

    private void addError(ValidationErrorType validationErrorType, @Nullable SourceLocation location, String description) {
        addError(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(location)
                .description(description));
    }

    private void addError(ValidationError.Builder validationError) {
        errorCollector.addError(validationError.queryPath(getQueryPath()).build());
    }

    private @Nullable List<String> getQueryPath() {
        return validationContext.getQueryPath();
    }

    private String i18n(ValidationErrorType validationErrorType, I18nMsg i18nMsg) {
        return i18n(validationErrorType, i18nMsg.getMsgKey(), i18nMsg.getMsgArguments());
    }

    private String i18n(ValidationErrorType validationErrorType, String msgKey, Object... msgArgs) {
        Object[] params = new Object[msgArgs.length + 1];
        params[0] = mkTypeAndPath(validationErrorType);
        arraycopy(msgArgs, 0, params, 1, msgArgs.length);
        return validationContext.i18n(msgKey, params);
    }

    private String mkTypeAndPath(ValidationErrorType validationErrorType) {
        List<String> queryPath = getQueryPath();
        StringBuilder sb = new StringBuilder();
        sb.append(validationErrorType);
        if (queryPath != null) {
            sb.append("@[").append(String.join("/", queryPath)).append("]");
        }
        return sb.toString();
    }

    private boolean isExperimentalApiKeyEnabled(String key) {
        Object value = validationContext.getGraphQLContext().get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    private void checkDocument(Document document) {
        if (isRuleEnabled(OperationValidationRule.EXECUTABLE_DEFINITIONS)) {
            validateExecutableDefinitions(document);
        }
    }

    private void checkArgument(Argument argument) {
        if (shouldRunDocumentLevelRules()) {
            if (isRuleEnabled(OperationValidationRule.ARGUMENTS_OF_CORRECT_TYPE)) {
                validateArgumentsOfCorrectType(argument);
            }
            if (isRuleEnabled(OperationValidationRule.KNOWN_ARGUMENT_NAMES)) {
                validateKnownArgumentNames(argument);
            }
        }
    }

    private void checkTypeName(TypeName typeName) {
        if (shouldRunDocumentLevelRules()) {
            if (isRuleEnabled(OperationValidationRule.KNOWN_TYPE_NAMES)) {
                validateKnownTypeNames(typeName);
            }
        }
    }

    private void checkVariableDefinition(VariableDefinition variableDefinition) {
        if (isRuleEnabled(OperationValidationRule.VARIABLE_DEFAULT_VALUES_OF_CORRECT_TYPE)) {
            validateVariableDefaultValuesOfCorrectType(variableDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.VARIABLES_ARE_INPUT_TYPES)) {
            validateVariablesAreInputTypes(variableDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.NO_UNDEFINED_VARIABLES)) {
            definedVariableNames.add(variableDefinition.getName());
        }
        if (isRuleEnabled(OperationValidationRule.NO_UNUSED_VARIABLES)) {
            unusedVars_variableDefinitions.add(variableDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.VARIABLE_TYPES_MATCH)) {
            if (variableDefinitionMap != null) {
                variableDefinitionMap.put(variableDefinition.getName(), variableDefinition);
            }
        }
    }

    private void checkField(Field field) {
        if (shouldRunDocumentLevelRules()) {
            if (isRuleEnabled(OperationValidationRule.FIELDS_ON_CORRECT_TYPE)) {
                validateFieldsOnCorrectType(field);
            }
            if (isRuleEnabled(OperationValidationRule.SCALAR_LEAVES)) {
                validateScalarLeaves(field);
            }
            if (isRuleEnabled(OperationValidationRule.PROVIDED_NON_NULL_ARGUMENTS)) {
                validateProvidedNonNullArguments_field(field);
            }
            if (isRuleEnabled(OperationValidationRule.UNIQUE_ARGUMENT_NAMES)) {
                validateUniqueArgumentNames_field(field);
            }
            if (isRuleEnabled(OperationValidationRule.UNIQUE_DIRECTIVE_NAMES_PER_LOCATION)) {
                validateUniqueDirectiveNamesPerLocation(field, field.getDirectives());
            }
        }
        // Good Faith Introspection: runs during fragment spread traversal too
        if (shouldRunOperationScopedRules() && isRuleEnabled(OperationValidationRule.GOOD_FAITH_INTROSPECTION)) {
            checkGoodFaithIntrospection(field);
        }
    }

    // --- GoodFaithIntrospection ---
    private void checkGoodFaithIntrospection(Field field) {
        GraphQLCompositeType parentType = validationContext.getParentType();
        if (parentType == null) {
            return;
        }
        String fieldName = field.getName();
        String key = null;

        // Check query-level introspection fields (__schema, __type).
        // Only counted at the structural level (not during fragment traversal) to match ENO merging
        // behavior where the same field from a direct selection and a fragment spread merge into one.
        if (shouldRunDocumentLevelRules()) {
            GraphQLObjectType queryType = validationContext.getSchema().getQueryType();
            if (queryType != null && parentType.getName().equals(queryType.getName())) {
                if ("__schema".equals(fieldName) || "__type".equals(fieldName)) {
                    key = parentType.getName() + "." + fieldName;
                }
            }
        }

        // Check __Type fields that can form cycles.
        // Counted during ALL traversals (including fragment spreads) because each occurrence
        // at a different depth represents a separate cycle risk.
        if ("__Type".equals(parentType.getName())) {
            if ("fields".equals(fieldName) || "inputFields".equals(fieldName)
                    || "interfaces".equals(fieldName) || "possibleTypes".equals(fieldName)) {
                key = "__Type." + fieldName;
            }
        }

        if (key != null) {
            int count = introspectionFieldCounts.merge(key, 1, Integer::sum);
            if (count > 1) {
                throw GoodFaithIntrospectionExceeded.tooManyFields(key);
            }
        }
    }

    private void checkInlineFragment(InlineFragment inlineFragment) {
        if (shouldRunDocumentLevelRules()) {
            if (isRuleEnabled(OperationValidationRule.FRAGMENTS_ON_COMPOSITE_TYPE)) {
                validateFragmentsOnCompositeType_inline(inlineFragment);
            }
            if (isRuleEnabled(OperationValidationRule.POSSIBLE_FRAGMENT_SPREADS)) {
                validatePossibleFragmentSpreads_inline(inlineFragment);
            }
            if (isRuleEnabled(OperationValidationRule.UNIQUE_DIRECTIVE_NAMES_PER_LOCATION)) {
                validateUniqueDirectiveNamesPerLocation(inlineFragment, inlineFragment.getDirectives());
            }
        }
    }

    private void checkDirective(Directive directive, List<Node> ancestors) {
        if (shouldRunDocumentLevelRules()) {
            if (isRuleEnabled(OperationValidationRule.KNOWN_DIRECTIVES)) {
                validateKnownDirectives(directive, ancestors);
            }
            if (isRuleEnabled(OperationValidationRule.PROVIDED_NON_NULL_ARGUMENTS)) {
                validateProvidedNonNullArguments_directive(directive);
            }
            if (isRuleEnabled(OperationValidationRule.UNIQUE_ARGUMENT_NAMES)) {
                validateUniqueArgumentNames_directive(directive);
            }
            if (isRuleEnabled(OperationValidationRule.DEFER_DIRECTIVE_LABEL)) {
                validateDeferDirectiveLabel(directive);
            }
        }
        if (shouldRunOperationScopedRules()) {
            if (isRuleEnabled(OperationValidationRule.DEFER_DIRECTIVE_ON_ROOT_LEVEL)) {
                validateDeferDirectiveOnRootLevel(directive);
            }
            if (isRuleEnabled(OperationValidationRule.DEFER_DIRECTIVE_ON_VALID_OPERATION)) {
                validateDeferDirectiveOnValidOperation(directive, ancestors);
            }
        }
    }

    private void checkFragmentSpread(FragmentSpread node, List<Node> ancestors) {
        if (shouldRunDocumentLevelRules()) {
            if (isRuleEnabled(OperationValidationRule.KNOWN_FRAGMENT_NAMES)) {
                validateKnownFragmentNames(node);
            }
            if (isRuleEnabled(OperationValidationRule.POSSIBLE_FRAGMENT_SPREADS)) {
                validatePossibleFragmentSpreads_spread(node);
            }
            if (isRuleEnabled(OperationValidationRule.NO_UNUSED_FRAGMENTS)) {
                unusedFragTracking_usedFragments.add(node.getName());
            }
            if (isRuleEnabled(OperationValidationRule.UNIQUE_DIRECTIVE_NAMES_PER_LOCATION)) {
                validateUniqueDirectiveNamesPerLocation(node, node.getDirectives());
            }
        }

        // Handle complexity tracking and fragment traversal
        if (shouldRunOperationScopedRules()) {
            String fragmentName = node.getName();
            FragmentDefinition fragment = validationContext.getFragment(fragmentName);

            if (visitedFragmentSpreads.contains(fragmentName)) {
                // Subsequent spread - add stored complexity (don't traverse again)
                FragmentComplexityInfo info = fragmentComplexityMap.get(fragmentName);
                if (info != null) {
                    fieldCount += info.getFieldCount();
                    checkFieldCountLimit();
                    int potentialDepth = currentFieldDepth + info.getMaxDepth();
                    checkDepthLimit(potentialDepth);
                    // Update max depth if we're inside a fragment traversal
                    if (fragmentRetraversalDepth > 0 && potentialDepth > fragmentTraversalMaxDepth) {
                        fragmentTraversalMaxDepth = potentialDepth;
                    }
                }
            } else if (fragment != null) {
                // First spread - traverse and track complexity
                visitedFragmentSpreads.add(fragmentName);

                int fieldCountBefore = fieldCount;
                int depthAtEntry = currentFieldDepth;
                int previousFragmentMaxDepth = fragmentTraversalMaxDepth;

                // Initialize max depth tracking for this fragment
                fragmentTraversalMaxDepth = currentFieldDepth;

                fragmentRetraversalDepth++;
                new LanguageTraversal(ancestors).traverse(fragment, this);
                fragmentRetraversalDepth--;

                // Calculate and store fragment complexity
                int fragmentFieldCount = fieldCount - fieldCountBefore;
                int fragmentMaxInternalDepth = fragmentTraversalMaxDepth - depthAtEntry;

                fragmentComplexityMap.put(fragmentName,
                        new FragmentComplexityInfo(fragmentFieldCount, fragmentMaxInternalDepth));

                // Restore max depth for outer fragment (if nested)
                if (fragmentRetraversalDepth > 0 && previousFragmentMaxDepth > fragmentTraversalMaxDepth) {
                    fragmentTraversalMaxDepth = previousFragmentMaxDepth;
                }
            }
        }
    }

    private void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        if (shouldRunDocumentLevelRules()) {
            if (isRuleEnabled(OperationValidationRule.FRAGMENTS_ON_COMPOSITE_TYPE)) {
                validateFragmentsOnCompositeType_definition(fragmentDefinition);
            }
            if (isRuleEnabled(OperationValidationRule.NO_FRAGMENT_CYCLES)) {
                validateNoFragmentCycles(fragmentDefinition);
            }
            if (isRuleEnabled(OperationValidationRule.NO_UNUSED_FRAGMENTS)) {
                allDeclaredFragments.add(fragmentDefinition);
                unusedFragTracking_usedFragments = new ArrayList<>();
                spreadsInDefinition.put(fragmentDefinition.getName(), unusedFragTracking_usedFragments);
            }
            if (isRuleEnabled(OperationValidationRule.UNIQUE_FRAGMENT_NAMES)) {
                validateUniqueFragmentNames(fragmentDefinition);
            }
            if (isRuleEnabled(OperationValidationRule.UNIQUE_DIRECTIVE_NAMES_PER_LOCATION)) {
                validateUniqueDirectiveNamesPerLocation(fragmentDefinition, fragmentDefinition.getDirectives());
            }
        }
    }

    private void checkOperationDefinition(OperationDefinition operationDefinition) {
        operationScope = true;

        if (isRuleEnabled(OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED)) {
            validateOverlappingFieldsCanBeMerged(operationDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.LONE_ANONYMOUS_OPERATION)) {
            validateLoneAnonymousOperation(operationDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.UNIQUE_OPERATION_NAMES)) {
            validateUniqueOperationNames(operationDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.UNIQUE_VARIABLE_NAMES)) {
            validateUniqueVariableNames(operationDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.SUBSCRIPTION_UNIQUE_ROOT_FIELD)) {
            validateSubscriptionUniqueRootField(operationDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.UNIQUE_DIRECTIVE_NAMES_PER_LOCATION)) {
            validateUniqueDirectiveNamesPerLocation(operationDefinition, operationDefinition.getDirectives());
        }
        if (isRuleEnabled(OperationValidationRule.KNOWN_OPERATION_TYPES)) {
            validateKnownOperationTypes(operationDefinition);
        }
        if (isRuleEnabled(OperationValidationRule.NO_UNUSED_FRAGMENTS)) {
            unusedFragTracking_usedFragments = new ArrayList<>();
            fragmentsUsedDirectlyInOperation.add(unusedFragTracking_usedFragments);
        }
        if (isRuleEnabled(OperationValidationRule.NO_UNDEFINED_VARIABLES)) {
            definedVariableNames.clear();
        }
        if (isRuleEnabled(OperationValidationRule.NO_UNUSED_VARIABLES)) {
            unusedVars_usedVariables.clear();
            unusedVars_variableDefinitions.clear();
        }
        if (isRuleEnabled(OperationValidationRule.VARIABLE_TYPES_MATCH)) {
            variableDefinitionMap = new LinkedHashMap<>();
        }
    }

    private void checkVariable(VariableReference variableReference) {
        if (shouldRunOperationScopedRules()) {
            if (isRuleEnabled(OperationValidationRule.NO_UNDEFINED_VARIABLES)) {
                validateNoUndefinedVariables(variableReference);
            }
            if (isRuleEnabled(OperationValidationRule.VARIABLE_TYPES_MATCH)) {
                validateVariableTypesMatch(variableReference);
            }
            if (isRuleEnabled(OperationValidationRule.NO_UNUSED_VARIABLES)) {
                unusedVars_usedVariables.add(variableReference.getName());
            }
        }
    }

    private void checkSelectionSet() {
        // No rules currently check selection set on enter
    }

    private void checkObjectValue(ObjectValue objectValue) {
        if (shouldRunDocumentLevelRules()) {
            if (isRuleEnabled(OperationValidationRule.UNIQUE_OBJECT_FIELD_NAME)) {
                validateUniqueObjectFieldName(objectValue);
            }
        }
    }

    private void leaveOperationDefinition() {
        // fragments should be revisited for each operation
        visitedFragmentSpreads.clear();
        operationScope = false;

        if (isRuleEnabled(OperationValidationRule.NO_UNUSED_VARIABLES)) {
            for (VariableDefinition variableDefinition : unusedVars_variableDefinitions) {
                if (!unusedVars_usedVariables.contains(variableDefinition.getName())) {
                    String message = i18n(UnusedVariable, "NoUnusedVariables.unusedVariable", variableDefinition.getName());
                    addError(UnusedVariable, variableDefinition.getSourceLocation(), message);
                }
            }
        }

        // Reset complexity counters for next operation
        fieldCount = 0;
        currentFieldDepth = 0;
        maxFieldDepthSeen = 0;
        fragmentTraversalMaxDepth = 0;
        introspectionFieldCounts.clear();
    }

    private void leaveSelectionSet() {
        // No rules currently use leaveSelectionSet
    }

    private void leaveFragmentDefinition() {
        // No special handling needed - the fragment spread depth tracking
        // is handled in checkFragmentSpread
    }

    private void documentFinished() {
        if (isRuleEnabled(OperationValidationRule.NO_UNUSED_FRAGMENTS)) {
            validateNoUnusedFragments();
        }
        if (isRuleEnabled(OperationValidationRule.LONE_ANONYMOUS_OPERATION)) {
            hasAnonymousOp = false;
        }
    }

    // --- ExecutableDefinitions ---
    private void validateExecutableDefinitions(Document document) {
        document.getDefinitions().forEach(definition -> {
            if (!(definition instanceof OperationDefinition)
                    && !(definition instanceof FragmentDefinition)) {
                String message = nonExecutableDefinitionMessage(definition);
                addError(NonExecutableDefinition, definition.getSourceLocation(), message);
            }
        });
    }

    private String nonExecutableDefinitionMessage(Definition definition) {
        if (definition instanceof TypeDefinition) {
            return i18n(NonExecutableDefinition, "ExecutableDefinitions.notExecutableType", ((TypeDefinition) definition).getName());
        } else if (definition instanceof SchemaDefinition) {
            return i18n(NonExecutableDefinition, "ExecutableDefinitions.notExecutableSchema");
        } else if (definition instanceof DirectiveDefinition) {
            return i18n(NonExecutableDefinition, "ExecutableDefinitions.notExecutableDirective", ((DirectiveDefinition) definition).getName());
        }
        return i18n(NonExecutableDefinition, "ExecutableDefinitions.notExecutableDefinition");
    }

    // --- ArgumentsOfCorrectType ---
    private void validateArgumentsOfCorrectType(Argument argument) {
        GraphQLArgument fieldArgument = validationContext.getArgument();
        if (fieldArgument == null) {
            return;
        }
        ArgumentValidationUtil argValidationUtil = new ArgumentValidationUtil(argument);
        if (!argValidationUtil.isValidLiteralValue(argument.getValue(), fieldArgument.getType(),
                validationContext.getSchema(), validationContext.getGraphQLContext(), validationContext.getI18n().getLocale())) {
            String message = i18n(WrongType, argValidationUtil.getMsgAndArgs());
            addError(newValidationError()
                    .validationErrorType(WrongType)
                    .sourceLocation(argument.getSourceLocation())
                    .description(message)
                    .extensions(argValidationUtil.getErrorExtensions()));
        }
    }

    // --- FieldsOnCorrectType ---
    private void validateFieldsOnCorrectType(Field field) {
        GraphQLCompositeType parentType = validationContext.getParentType();
        if (parentType == null) {
            return;
        }
        GraphQLFieldDefinition fieldDef = validationContext.getFieldDef();
        if (fieldDef == null) {
            String message = i18n(FieldUndefined, "FieldsOnCorrectType.unknownField", field.getName(), parentType.getName());
            addError(FieldUndefined, field.getSourceLocation(), message);
        }
    }

    // --- FragmentsOnCompositeType ---
    private void validateFragmentsOnCompositeType_inline(InlineFragment inlineFragment) {
        if (inlineFragment.getTypeCondition() == null) {
            return;
        }
        GraphQLType type = validationContext.getSchema().getType(inlineFragment.getTypeCondition().getName());
        if (type == null) {
            return;
        }
        if (!(type instanceof GraphQLCompositeType)) {
            String message = i18n(InlineFragmentTypeConditionInvalid, "FragmentsOnCompositeType.invalidInlineTypeCondition");
            addError(InlineFragmentTypeConditionInvalid, inlineFragment.getSourceLocation(), message);
        }
    }

    private void validateFragmentsOnCompositeType_definition(FragmentDefinition fragmentDefinition) {
        GraphQLType type = validationContext.getSchema().getType(fragmentDefinition.getTypeCondition().getName());
        if (type == null) {
            return;
        }
        if (!(type instanceof GraphQLCompositeType)) {
            String message = i18n(FragmentTypeConditionInvalid, "FragmentsOnCompositeType.invalidFragmentTypeCondition");
            addError(FragmentTypeConditionInvalid, fragmentDefinition.getSourceLocation(), message);
        }
    }

    // --- KnownArgumentNames ---
    private void validateKnownArgumentNames(Argument argument) {
        GraphQLDirective directiveDef = validationContext.getDirective();
        if (directiveDef != null) {
            GraphQLArgument directiveArgument = directiveDef.getArgument(argument.getName());
            if (directiveArgument == null) {
                String message = i18n(UnknownDirective, "KnownArgumentNames.unknownDirectiveArg", argument.getName());
                addError(UnknownDirective, argument.getSourceLocation(), message);
            }
            return;
        }
        GraphQLFieldDefinition fieldDef = validationContext.getFieldDef();
        if (fieldDef == null) {
            return;
        }
        GraphQLArgument fieldArgument = fieldDef.getArgument(argument.getName());
        if (fieldArgument == null) {
            String message = i18n(UnknownArgument, "KnownArgumentNames.unknownFieldArg", argument.getName());
            addError(UnknownArgument, argument.getSourceLocation(), message);
        }
    }

    // --- KnownDirectives ---
    private void validateKnownDirectives(Directive directive, List<Node> ancestors) {
        GraphQLDirective graphQLDirective = validationContext.getSchema().getDirective(directive.getName());
        if (graphQLDirective == null) {
            String message = i18n(UnknownDirective, "KnownDirectives.unknownDirective", directive.getName());
            addError(UnknownDirective, directive.getSourceLocation(), message);
            return;
        }
        Node ancestor = ancestors.get(ancestors.size() - 1);
        if (hasInvalidLocation(graphQLDirective, ancestor)) {
            String message = i18n(MisplacedDirective, "KnownDirectives.directiveNotAllowed", directive.getName());
            addError(MisplacedDirective, directive.getSourceLocation(), message);
        }
    }

    private boolean hasInvalidLocation(GraphQLDirective directive, Node ancestor) {
        EnumSet<DirectiveLocation> validLocations = directive.validLocations();
        if (ancestor instanceof OperationDefinition) {
            OperationDefinition.Operation operation = ((OperationDefinition) ancestor).getOperation();
            if (OperationDefinition.Operation.QUERY.equals(operation)) {
                return !validLocations.contains(DirectiveLocation.QUERY);
            } else if (OperationDefinition.Operation.MUTATION.equals(operation)) {
                return !validLocations.contains(DirectiveLocation.MUTATION);
            } else if (OperationDefinition.Operation.SUBSCRIPTION.equals(operation)) {
                return !validLocations.contains(DirectiveLocation.SUBSCRIPTION);
            }
        } else if (ancestor instanceof Field) {
            return !(validLocations.contains(DirectiveLocation.FIELD));
        } else if (ancestor instanceof FragmentSpread) {
            return !(validLocations.contains(DirectiveLocation.FRAGMENT_SPREAD));
        } else if (ancestor instanceof FragmentDefinition) {
            return !(validLocations.contains(DirectiveLocation.FRAGMENT_DEFINITION));
        } else if (ancestor instanceof InlineFragment) {
            return !(validLocations.contains(DirectiveLocation.INLINE_FRAGMENT));
        } else if (ancestor instanceof VariableDefinition) {
            return !(validLocations.contains(DirectiveLocation.VARIABLE_DEFINITION));
        }
        return true;
    }

    // --- KnownFragmentNames ---
    private void validateKnownFragmentNames(FragmentSpread fragmentSpread) {
        FragmentDefinition fragmentDefinition = validationContext.getFragment(fragmentSpread.getName());
        if (fragmentDefinition == null) {
            String message = i18n(UndefinedFragment, "KnownFragmentNames.undefinedFragment", fragmentSpread.getName());
            addError(UndefinedFragment, fragmentSpread.getSourceLocation(), message);
        }
    }

    // --- KnownTypeNames ---
    private void validateKnownTypeNames(TypeName typeName) {
        if (validationContext.getSchema().getType(typeName.getName()) == null) {
            String message = i18n(UnknownType, "KnownTypeNames.unknownType", typeName.getName());
            addError(UnknownType, typeName.getSourceLocation(), message);
        }
    }

    // --- NoFragmentCycles ---
    private void prepareFragmentSpreadsMap() {
        List<Definition> definitions = validationContext.getDocument().getDefinitions();
        for (Definition definition : definitions) {
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentSpreadsMap.put(fragmentDefinition.getName(), gatherSpreads(fragmentDefinition));
            }
        }
    }

    private Set<String> gatherSpreads(FragmentDefinition fragmentDefinition) {
        final Set<String> spreads = new HashSet<>();
        DocumentVisitor visitor = new DocumentVisitor() {
            @Override
            public void enter(Node node, List<Node> path) {
                if (node instanceof FragmentSpread) {
                    spreads.add(((FragmentSpread) node).getName());
                }
            }

            @Override
            public void leave(Node node, List<Node> path) {
            }
        };
        new LanguageTraversal().traverse(fragmentDefinition, visitor);
        return spreads;
    }

    private void validateNoFragmentCycles(FragmentDefinition fragmentDefinition) {
        ArrayList<String> path = new ArrayList<>();
        path.add(fragmentDefinition.getName());
        Map<String, Set<String>> transitiveSpreads = buildTransitiveSpreads(path, new HashMap<>());

        for (Map.Entry<String, Set<String>> entry : transitiveSpreads.entrySet()) {
            if (entry.getValue().contains(entry.getKey())) {
                String message = i18n(FragmentCycle, "NoFragmentCycles.cyclesNotAllowed");
                addError(FragmentCycle, Collections.singletonList(fragmentDefinition), message);
            }
        }
    }

    private Map<String, Set<String>> buildTransitiveSpreads(ArrayList<String> path, Map<String, Set<String>> transitiveSpreads) {
        String name = path.get(path.size() - 1);
        if (transitiveSpreads.containsKey(name)) {
            return transitiveSpreads;
        }
        Set<String> spreads = fragmentSpreadsMap.get(name);
        if (spreads == null || spreads.isEmpty()) {
            return transitiveSpreads;
        }
        for (String ancestor : path) {
            Set<String> ancestorSpreads = transitiveSpreads.get(ancestor);
            if (ancestorSpreads == null) {
                ancestorSpreads = new HashSet<>();
            }
            ancestorSpreads.addAll(spreads);
            transitiveSpreads.put(ancestor, ancestorSpreads);
        }
        for (String child : spreads) {
            if (path.contains(child) || transitiveSpreads.containsKey(child)) {
                continue;
            }
            ArrayList<String> childPath = new ArrayList<>(path);
            childPath.add(child);
            buildTransitiveSpreads(childPath, transitiveSpreads);
        }
        return transitiveSpreads;
    }

    // --- NoUndefinedVariables ---
    private void validateNoUndefinedVariables(VariableReference variableReference) {
        if (!definedVariableNames.contains(variableReference.getName())) {
            String message = i18n(UndefinedVariable, "NoUndefinedVariables.undefinedVariable", variableReference.getName());
            addError(UndefinedVariable, variableReference.getSourceLocation(), message);
        }
    }

    // --- NoUnusedFragments ---
    private void validateNoUnusedFragments() {
        Set<String> allUsedFragments = new HashSet<>();
        for (List<String> fragmentsInOneOperation : fragmentsUsedDirectlyInOperation) {
            for (String fragment : fragmentsInOneOperation) {
                collectUsedFragmentsInDefinition(allUsedFragments, fragment);
            }
        }
        for (FragmentDefinition fragmentDefinition : allDeclaredFragments) {
            if (!allUsedFragments.contains(fragmentDefinition.getName())) {
                String message = i18n(UnusedFragment, "NoUnusedFragments.unusedFragments", fragmentDefinition.getName());
                addError(UnusedFragment, fragmentDefinition.getSourceLocation(), message);
            }
        }
    }

    private void collectUsedFragmentsInDefinition(Set<String> result, String fragmentName) {
        if (!result.add(fragmentName)) {
            return;
        }
        List<String> spreadList = spreadsInDefinition.get(fragmentName);
        if (spreadList == null) {
            return;
        }
        for (String fragment : spreadList) {
            collectUsedFragmentsInDefinition(result, fragment);
        }
    }

    // --- OverlappingFieldsCanBeMerged ---
    private void validateOverlappingFieldsCanBeMerged(OperationDefinition operationDefinition) {
        overlappingFieldsImpl(operationDefinition.getSelectionSet(), validationContext.getOutputType());
    }

    private void overlappingFieldsImpl(SelectionSet selectionSet, @Nullable GraphQLOutputType graphQLOutputType) {
        Map<String, Set<FieldAndType>> fieldMap = new LinkedHashMap<>(selectionSet.getSelections().size());
        Set<String> visitedFragments = new LinkedHashSet<>();
        overlappingFields_collectFields(fieldMap, selectionSet, graphQLOutputType, visitedFragments);
        List<Conflict> conflicts = findConflicts(fieldMap);
        for (Conflict conflict : conflicts) {
            if (conflictsReported.contains(conflict.fields)) {
                continue;
            }
            conflictsReported.add(conflict.fields);
            addError(FieldsConflict, conflict.fields, conflict.reason);
        }
    }

    private void overlappingFields_collectFields(Map<String, Set<FieldAndType>> fieldMap, SelectionSet selectionSet, @Nullable GraphQLType parentType, Set<String> visitedFragments) {
        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                overlappingFields_collectFieldsForField(fieldMap, parentType, (Field) selection);
            } else if (selection instanceof InlineFragment) {
                overlappingFields_collectFieldsForInlineFragment(fieldMap, visitedFragments, parentType, (InlineFragment) selection);
            } else if (selection instanceof FragmentSpread) {
                overlappingFields_collectFieldsForFragmentSpread(fieldMap, visitedFragments, (FragmentSpread) selection);
            }
        }
    }

    private void overlappingFields_collectFieldsForFragmentSpread(Map<String, Set<FieldAndType>> fieldMap, Set<String> visitedFragments, FragmentSpread fragmentSpread) {
        FragmentDefinition fragment = validationContext.getFragment(fragmentSpread.getName());
        if (fragment == null) {
            return;
        }
        if (visitedFragments.contains(fragment.getName())) {
            return;
        }
        visitedFragments.add(fragment.getName());
        GraphQLType graphQLType = TypeFromAST.getTypeFromAST(validationContext.getSchema(), fragment.getTypeCondition());
        overlappingFields_collectFields(fieldMap, fragment.getSelectionSet(), graphQLType, visitedFragments);
    }

    private void overlappingFields_collectFieldsForInlineFragment(Map<String, Set<FieldAndType>> fieldMap, Set<String> visitedFragments, @Nullable GraphQLType parentType, InlineFragment inlineFragment) {
        GraphQLType graphQLType;
        if (inlineFragment.getTypeCondition() == null) {
            graphQLType = parentType;
        } else {
            graphQLType = TypeFromAST.getTypeFromAST(validationContext.getSchema(), inlineFragment.getTypeCondition());
        }
        overlappingFields_collectFields(fieldMap, inlineFragment.getSelectionSet(), graphQLType, visitedFragments);
    }

    private void overlappingFields_collectFieldsForField(Map<String, Set<FieldAndType>> fieldMap, @Nullable GraphQLType parentType, Field field) {
        String responseName = field.getResultKey();
        GraphQLOutputType fieldType = null;
        GraphQLUnmodifiedType unwrappedParent = parentType != null ? unwrapAll(parentType) : null;
        if (unwrappedParent instanceof GraphQLFieldsContainer) {
            GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) unwrappedParent;
            GraphQLFieldDefinition fieldDefinition = validationContext.getSchema().getCodeRegistry().getFieldVisibility().getFieldDefinition(fieldsContainer, field.getName());
            fieldType = fieldDefinition != null ? fieldDefinition.getType() : null;
        }
        fieldMap.computeIfAbsent(responseName, k -> new LinkedHashSet<>()).add(new FieldAndType(field, fieldType, unwrappedParent));
    }

    private List<Conflict> findConflicts(Map<String, Set<FieldAndType>> fieldMap) {
        List<Conflict> result = new ArrayList<>();
        sameResponseShapeByName(fieldMap, emptyList(), result);
        sameForCommonParentsByName(fieldMap, emptyList(), result);
        return result;
    }

    private void sameResponseShapeByName(Map<String, Set<FieldAndType>> fieldMap, ImmutableList<String> currentPath, List<Conflict> conflictsResult) {
        for (Map.Entry<String, Set<FieldAndType>> entry : fieldMap.entrySet()) {
            if (sameResponseShapeChecked.contains(entry.getValue())) {
                continue;
            }
            ImmutableList<String> newPath = addToList(currentPath, entry.getKey());
            sameResponseShapeChecked.add(entry.getValue());
            Conflict conflict = requireSameOutputTypeShape(newPath, entry.getValue());
            if (conflict != null) {
                conflictsResult.add(conflict);
                continue;
            }
            Map<String, Set<FieldAndType>> subSelections = mergeSubSelections(entry.getValue());
            sameResponseShapeByName(subSelections, newPath, conflictsResult);
        }
    }

    private Map<String, Set<FieldAndType>> mergeSubSelections(Set<FieldAndType> sameNameFields) {
        Map<String, Set<FieldAndType>> fieldMap = new LinkedHashMap<>();
        for (FieldAndType fieldAndType : sameNameFields) {
            if (fieldAndType.field.getSelectionSet() != null) {
                Set<String> visitedFragments = new LinkedHashSet<>();
                overlappingFields_collectFields(fieldMap, fieldAndType.field.getSelectionSet(), fieldAndType.graphQLType, visitedFragments);
            }
        }
        return fieldMap;
    }

    private void sameForCommonParentsByName(Map<String, Set<FieldAndType>> fieldMap, ImmutableList<String> currentPath, List<Conflict> conflictsResult) {
        for (Map.Entry<String, Set<FieldAndType>> entry : fieldMap.entrySet()) {
            List<Set<FieldAndType>> groups = groupByCommonParents(entry.getValue());
            ImmutableList<String> newPath = addToList(currentPath, entry.getKey());
            for (Set<FieldAndType> group : groups) {
                if (sameForCommonParentsChecked.contains(group)) {
                    continue;
                }
                sameForCommonParentsChecked.add(group);
                Conflict conflict = requireSameNameAndArguments(newPath, group);
                if (conflict != null) {
                    conflictsResult.add(conflict);
                    continue;
                }
                Map<String, Set<FieldAndType>> subSelections = mergeSubSelections(group);
                sameForCommonParentsByName(subSelections, newPath, conflictsResult);
            }
        }
    }

    private List<Set<FieldAndType>> groupByCommonParents(Set<FieldAndType> fields) {
        // Single-pass: partition into abstract types and concrete groups simultaneously
        List<FieldAndType> abstractTypes = null;
        Map<GraphQLType, Set<FieldAndType>> concreteGroups = null;

        for (FieldAndType fieldAndType : fields) {
            if (isInterfaceOrUnion(fieldAndType.parentType)) {
                if (abstractTypes == null) {
                    abstractTypes = new ArrayList<>();
                }
                abstractTypes.add(fieldAndType);
            } else if (fieldAndType.parentType instanceof GraphQLObjectType) {
                if (concreteGroups == null) {
                    concreteGroups = new LinkedHashMap<>();
                }
                concreteGroups.computeIfAbsent(fieldAndType.parentType, k -> new LinkedHashSet<>()).add(fieldAndType);
            }
        }

        if (concreteGroups == null || concreteGroups.isEmpty()) {
            // No concrete types — return all abstract types as a single group
            if (abstractTypes == null) {
                return Collections.singletonList(fields);
            }
            return Collections.singletonList(new LinkedHashSet<>(abstractTypes));
        }

        List<Set<FieldAndType>> result = new ArrayList<>(concreteGroups.size());
        for (Set<FieldAndType> concreteGroup : concreteGroups.values()) {
            if (abstractTypes != null) {
                concreteGroup.addAll(abstractTypes);
            }
            result.add(concreteGroup);
        }
        return result;
    }

    private boolean isInterfaceOrUnion(@Nullable GraphQLType type) {
        return type instanceof GraphQLInterfaceType || type instanceof GraphQLUnionType;
    }

    private @Nullable Conflict requireSameNameAndArguments(ImmutableList<String> path, Set<FieldAndType> fieldAndTypes) {
        if (fieldAndTypes.size() <= 1) {
            return null;
        }
        String name = null;
        List<Argument> arguments = null;
        List<Field> fields = new ArrayList<>();
        for (FieldAndType fieldAndType : fieldAndTypes) {
            Field field = fieldAndType.field;
            fields.add(field);
            if (name == null) {
                name = field.getName();
                arguments = field.getArguments();
                continue;
            }
            if (!field.getName().equals(name)) {
                String reason = i18n(FieldsConflict, "OverlappingFieldsCanBeMerged.differentFields", pathToString(path), name, field.getName());
                return new Conflict(reason, fields);
            }
            if (!sameArguments(field.getArguments(), arguments)) {
                String reason = i18n(FieldsConflict, "OverlappingFieldsCanBeMerged.differentArgs", pathToString(path));
                return new Conflict(reason, fields);
            }
        }
        return null;
    }

    private String pathToString(ImmutableList<String> path) {
        return String.join("/", path);
    }

    private boolean sameArguments(List<Argument> arguments1, @Nullable List<Argument> arguments2) {
        if (arguments2 == null || arguments1.size() != arguments2.size()) {
            return false;
        }
        for (Argument argument : arguments1) {
            Argument matchedArgument = findArgumentByName(argument.getName(), arguments2);
            if (matchedArgument == null) {
                return false;
            }
            if (!AstComparator.sameValue(argument.getValue(), matchedArgument.getValue())) {
                return false;
            }
        }
        return true;
    }

    private @Nullable Argument findArgumentByName(String name, List<Argument> arguments) {
        for (Argument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

    private @Nullable Conflict requireSameOutputTypeShape(ImmutableList<String> path, Set<FieldAndType> fieldAndTypes) {
        if (fieldAndTypes.size() <= 1) {
            return null;
        }
        List<Field> fields = new ArrayList<>();
        GraphQLType typeAOriginal = null;
        for (FieldAndType fieldAndType : fieldAndTypes) {
            fields.add(fieldAndType.field);
            if (typeAOriginal == null) {
                typeAOriginal = fieldAndType.graphQLType;
                continue;
            }
            GraphQLType typeA = typeAOriginal;
            GraphQLType typeB = fieldAndType.graphQLType;
            if (typeB == null) {
                return mkNotSameTypeError(path, fields, typeA, typeB);
            }
            while (true) {
                if (isNonNull(typeA) || isNonNull(typeB)) {
                    if (isNullable(typeA) || isNullable(typeB)) {
                        String reason = i18n(FieldsConflict, "OverlappingFieldsCanBeMerged.differentNullability", pathToString(path));
                        return new Conflict(reason, fields);
                    }
                }
                if (isList(typeA) || isList(typeB)) {
                    if (!isList(typeA) || !isList(typeB)) {
                        String reason = i18n(FieldsConflict, "OverlappingFieldsCanBeMerged.differentLists", pathToString(path));
                        return new Conflict(reason, fields);
                    }
                }
                if (isNotWrapped(typeA) && isNotWrapped(typeB)) {
                    break;
                }
                typeA = unwrapOne(typeA);
                typeB = unwrapOne(typeB);
            }
            if (isScalar(typeA) || isScalar(typeB)) {
                if (notSameType(typeA, typeB)) {
                    return mkNotSameTypeError(path, fields, typeA, typeB);
                }
            }
            if (isEnum(typeA) || isEnum(typeB)) {
                if (notSameType(typeA, typeB)) {
                    return mkNotSameTypeError(path, fields, typeA, typeB);
                }
            }
        }
        return null;
    }

    private Conflict mkNotSameTypeError(ImmutableList<String> path, List<Field> fields, @Nullable GraphQLType typeA, @Nullable GraphQLType typeB) {
        String name1 = typeA != null ? simplePrint(typeA) : "null";
        String name2 = typeB != null ? simplePrint(typeB) : "null";
        String reason = i18n(FieldsConflict, "OverlappingFieldsCanBeMerged.differentReturnTypes", pathToString(path), name1, name2);
        return new Conflict(reason, fields);
    }

    private boolean notSameType(@Nullable GraphQLType type1, @Nullable GraphQLType type2) {
        if (type1 == null || type2 == null) {
            return false;
        }
        return !type1.equals(type2);
    }

    private static class FieldAndType {
        final Field field;
        final @Nullable GraphQLType graphQLType;
        final @Nullable GraphQLType parentType;

        public FieldAndType(Field field, @Nullable GraphQLType graphQLType, @Nullable GraphQLType parentType) {
            this.field = field;
            this.graphQLType = graphQLType;
            this.parentType = parentType;
        }

        @Override
        public String toString() {
            return "FieldAndType{" +
                    "field=" + field +
                    ", graphQLType=" + graphQLType +
                    ", parentType=" + parentType +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FieldAndType that = (FieldAndType) o;
            return Objects.equals(field, that.field);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(field);
        }
    }

    private static class Conflict {
        final String reason;
        final Set<Field> fields = new LinkedHashSet<>();

        public Conflict(String reason, List<Field> fields) {
            this.reason = reason;
            this.fields.addAll(fields);
        }
    }

    // --- PossibleFragmentSpreads ---
    private void validatePossibleFragmentSpreads_inline(InlineFragment inlineFragment) {
        GraphQLOutputType fragType = validationContext.getOutputType();
        GraphQLCompositeType parentType = validationContext.getParentType();
        if (fragType == null || parentType == null) {
            return;
        }

        if (isValidTargetCompositeType(fragType) && isValidTargetCompositeType(parentType) && typesDoNotOverlap(fragType, parentType)) {
            String message = i18n(InvalidFragmentType, "PossibleFragmentSpreads.inlineIncompatibleTypes", parentType.getName(), simplePrint(fragType));
            addError(InvalidFragmentType, inlineFragment.getSourceLocation(), message);
        }
    }

    private void validatePossibleFragmentSpreads_spread(FragmentSpread fragmentSpread) {
        FragmentDefinition fragment = validationContext.getFragment(fragmentSpread.getName());
        if (fragment == null) {
            return;
        }
        GraphQLType typeCondition = TypeFromAST.getTypeFromAST(validationContext.getSchema(), fragment.getTypeCondition());
        GraphQLCompositeType parentType = validationContext.getParentType();
        if (typeCondition == null || parentType == null) {
            return;
        }

        if (isValidTargetCompositeType(typeCondition) && isValidTargetCompositeType(parentType) && typesDoNotOverlap(typeCondition, parentType)) {
            String message = i18n(InvalidFragmentType, "PossibleFragmentSpreads.fragmentIncompatibleTypes", fragmentSpread.getName(), parentType.getName(), simplePrint(typeCondition));
            addError(InvalidFragmentType, fragmentSpread.getSourceLocation(), message);
        }
    }

    private boolean typesDoNotOverlap(GraphQLType type, GraphQLCompositeType parent) {
        if (type == parent) {
            return false;
        }
        List<? extends GraphQLType> possibleParentTypes = getPossibleType(parent);
        List<? extends GraphQLType> possibleConditionTypes = getPossibleType(type);
        return Collections.disjoint(possibleParentTypes, possibleConditionTypes);
    }

    private List<? extends GraphQLType> getPossibleType(GraphQLType type) {
        if (type instanceof GraphQLObjectType) {
            return Collections.singletonList(type);
        } else if (type instanceof GraphQLInterfaceType) {
            List<GraphQLObjectType> implementations = validationContext.getSchema().getImplementations((GraphQLInterfaceType) type);
            return implementations != null ? implementations : Collections.emptyList();
        } else if (type instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) type).getTypes();
        } else {
            Assert.assertShouldNeverHappen();
        }
        return Collections.emptyList();
    }

    private boolean isValidTargetCompositeType(GraphQLType type) {
        return type instanceof GraphQLCompositeType;
    }

    // --- ProvidedNonNullArguments ---
    private void validateProvidedNonNullArguments_field(Field field) {
        GraphQLFieldDefinition fieldDef = validationContext.getFieldDef();
        if (fieldDef == null) {
            return;
        }
        List<Argument> providedArguments = field.getArguments();

        for (GraphQLArgument graphQLArgument : fieldDef.getArguments()) {
            Argument argument = findArgumentByName(providedArguments, graphQLArgument.getName());
            boolean nonNullType = isNonNull(graphQLArgument.getType());
            boolean noDefaultValue = graphQLArgument.getArgumentDefaultValue().isNotSet();
            if (argument == null && nonNullType && noDefaultValue) {
                String message = i18n(MissingFieldArgument, "ProvidedNonNullArguments.missingFieldArg", graphQLArgument.getName());
                addError(MissingFieldArgument, field.getSourceLocation(), message);
            }
            if (argument != null) {
                Value value = argument.getValue();
                if (value instanceof NullValue && nonNullType && noDefaultValue) {
                    String message = i18n(NullValueForNonNullArgument, "ProvidedNonNullArguments.nullValue", graphQLArgument.getName());
                    addError(NullValueForNonNullArgument, field.getSourceLocation(), message);
                }
            }
        }
    }

    private void validateProvidedNonNullArguments_directive(Directive directive) {
        GraphQLDirective graphQLDirective = validationContext.getDirective();
        if (graphQLDirective == null) {
            return;
        }
        List<Argument> providedArguments = directive.getArguments();

        for (GraphQLArgument graphQLArgument : graphQLDirective.getArguments()) {
            Argument argument = findArgumentByName(providedArguments, graphQLArgument.getName());
            boolean nonNullType = isNonNull(graphQLArgument.getType());
            boolean noDefaultValue = graphQLArgument.getArgumentDefaultValue().isNotSet();
            if (argument == null && nonNullType && noDefaultValue) {
                String message = i18n(MissingDirectiveArgument, "ProvidedNonNullArguments.missingDirectiveArg", graphQLArgument.getName());
                addError(MissingDirectiveArgument, directive.getSourceLocation(), message);
            }
        }
    }

    private static @Nullable Argument findArgumentByName(List<Argument> arguments, String name) {
        for (Argument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

    // --- ScalarLeaves ---
    private void validateScalarLeaves(Field field) {
        GraphQLOutputType type = validationContext.getOutputType();
        if (type == null) {
            return;
        }
        if (isLeaf(type)) {
            if (field.getSelectionSet() != null) {
                String message = i18n(SubselectionNotAllowed, "ScalarLeaves.subselectionOnLeaf", simplePrint(type), field.getName());
                addError(SubselectionNotAllowed, field.getSourceLocation(), message);
            }
        } else {
            if (field.getSelectionSet() == null) {
                String message = i18n(SubselectionRequired, "ScalarLeaves.subselectionRequired", simplePrint(type), field.getName());
                addError(SubselectionRequired, field.getSourceLocation(), message);
            }
        }
    }

    // --- VariableDefaultValuesOfCorrectType ---
    private void validateVariableDefaultValuesOfCorrectType(VariableDefinition variableDefinition) {
        GraphQLInputType inputType = validationContext.getInputType();
        if (inputType == null) {
            return;
        }
        if (variableDefinition.getDefaultValue() != null
                && !validationUtil.isValidLiteralValue(variableDefinition.getDefaultValue(), inputType,
                validationContext.getSchema(), validationContext.getGraphQLContext(), validationContext.getI18n().getLocale())) {
            String message = i18n(BadValueForDefaultArg, "VariableDefaultValuesOfCorrectType.badDefault", variableDefinition.getDefaultValue(), simplePrint(inputType));
            addError(BadValueForDefaultArg, variableDefinition.getSourceLocation(), message);
        }
    }

    // --- VariablesAreInputTypes ---
    private void validateVariablesAreInputTypes(VariableDefinition variableDefinition) {
        TypeName unmodifiedAstType = validationUtil.getUnmodifiedType(variableDefinition.getType());
        GraphQLType type = validationContext.getSchema().getType(unmodifiedAstType.getName());
        if (type == null) {
            return;
        }
        if (!isInput(type)) {
            String message = i18n(NonInputTypeOnVariable, "VariablesAreInputTypes.wrongType", variableDefinition.getName(), unmodifiedAstType.getName());
            addError(NonInputTypeOnVariable, variableDefinition.getSourceLocation(), message);
        }
    }

    // --- VariableTypesMatch ---
    private void validateVariableTypesMatch(VariableReference variableReference) {
        if (variableDefinitionMap == null) {
            return;
        }
        VariableDefinition variableDefinition = variableDefinitionMap.get(variableReference.getName());
        if (variableDefinition == null) {
            return;
        }
        GraphQLType variableType = TypeFromAST.getTypeFromAST(validationContext.getSchema(), variableDefinition.getType());
        if (variableType == null) {
            return;
        }
        GraphQLInputType locationType = validationContext.getInputType();
        Optional<InputValueWithState> locationDefault = Optional.ofNullable(validationContext.getDefaultValue());
        if (locationType == null) {
            return;
        }
        Value<?> locationDefaultValue = null;
        if (locationDefault.isPresent() && locationDefault.get().isLiteral()) {
            locationDefaultValue = (Value<?>) locationDefault.get().getValue();
        } else if (locationDefault.isPresent() && locationDefault.get().isSet()) {
            locationDefaultValue = ValuesResolver.valueToLiteral(locationDefault.get(), locationType,
                    validationContext.getGraphQLContext(), validationContext.getI18n().getLocale());
        }
        boolean variableDefMatches = variablesTypesMatcher.doesVariableTypesMatch(variableType, variableDefinition.getDefaultValue(), locationType, locationDefaultValue);
        if (!variableDefMatches) {
            GraphQLType effectiveType = variablesTypesMatcher.effectiveType(variableType, variableDefinition.getDefaultValue());
            String message = i18n(VariableTypeMismatch, "VariableTypesMatchRule.unexpectedType",
                    variableDefinition.getName(),
                    GraphQLTypeUtil.simplePrint(effectiveType),
                    GraphQLTypeUtil.simplePrint(locationType));
            addError(VariableTypeMismatch, variableReference.getSourceLocation(), message);
        }
    }

    // --- LoneAnonymousOperation ---
    private void validateLoneAnonymousOperation(OperationDefinition operationDefinition) {
        String name = operationDefinition.getName();
        if (name == null) {
            hasAnonymousOp = true;
            if (loneAnon_count > 0) {
                String message = i18n(LoneAnonymousOperationViolation, "LoneAnonymousOperation.withOthers");
                addError(LoneAnonymousOperationViolation, operationDefinition.getSourceLocation(), message);
            }
        } else {
            if (hasAnonymousOp) {
                String message = i18n(LoneAnonymousOperationViolation, "LoneAnonymousOperation.namedOperation", name);
                addError(LoneAnonymousOperationViolation, operationDefinition.getSourceLocation(), message);
            }
        }
        loneAnon_count++;
    }

    // --- UniqueOperationNames ---
    private void validateUniqueOperationNames(OperationDefinition operationDefinition) {
        String name = operationDefinition.getName();
        if (name == null) {
            return;
        }
        if (operationNames.contains(name)) {
            String message = i18n(DuplicateOperationName, "UniqueOperationNames.oneOperation", operationDefinition.getName());
            addError(DuplicateOperationName, operationDefinition.getSourceLocation(), message);
        } else {
            operationNames.add(name);
        }
    }

    // --- UniqueFragmentNames ---
    private void validateUniqueFragmentNames(FragmentDefinition fragmentDefinition) {
        String name = fragmentDefinition.getName();
        if (name == null) {
            return;
        }
        if (fragmentNames.contains(name)) {
            String message = i18n(DuplicateFragmentName, "UniqueFragmentNames.oneFragment", name);
            addError(DuplicateFragmentName, fragmentDefinition.getSourceLocation(), message);
        } else {
            fragmentNames.add(name);
        }
    }

    // --- UniqueDirectiveNamesPerLocation ---
    private void validateUniqueDirectiveNamesPerLocation(Node<?> directivesContainer, List<Directive> directives) {
        Set<String> directiveNames = new LinkedHashSet<>();
        for (Directive directive : directives) {
            String name = directive.getName();
            GraphQLDirective graphQLDirective = validationContext.getSchema().getDirective(name);
            boolean nonRepeatable = graphQLDirective != null && graphQLDirective.isNonRepeatable();
            if (directiveNames.contains(name) && nonRepeatable) {
                String message = i18n(DuplicateDirectiveName, "UniqueDirectiveNamesPerLocation.uniqueDirectives", name, directivesContainer.getClass().getSimpleName());
                addError(DuplicateDirectiveName, directive.getSourceLocation(), message);
            } else {
                directiveNames.add(name);
            }
        }
    }

    // --- UniqueArgumentNames ---
    private void validateUniqueArgumentNames_field(Field field) {
        validateUniqueArgumentNames(field.getArguments(), field.getSourceLocation());
    }

    private void validateUniqueArgumentNames_directive(Directive directive) {
        validateUniqueArgumentNames(directive.getArguments(), directive.getSourceLocation());
    }

    private void validateUniqueArgumentNames(List<Argument> argumentList, @Nullable SourceLocation sourceLocation) {
        if (argumentList.size() <= 1) {
            return;
        }
        Set<String> arguments = Sets.newHashSetWithExpectedSize(argumentList.size());
        for (Argument argument : argumentList) {
            if (arguments.contains(argument.getName())) {
                String message = i18n(DuplicateArgumentNames, "UniqueArgumentNames.uniqueArgument", argument.getName());
                addError(DuplicateArgumentNames, sourceLocation, message);
            } else {
                arguments.add(argument.getName());
            }
        }
    }

    // --- UniqueVariableNames ---
    private void validateUniqueVariableNames(OperationDefinition operationDefinition) {
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();
        if (variableDefinitions == null || variableDefinitions.size() <= 1) {
            return;
        }
        Set<String> variableNameList = Sets.newLinkedHashSetWithExpectedSize(variableDefinitions.size());
        for (VariableDefinition variableDefinition : variableDefinitions) {
            if (variableNameList.contains(variableDefinition.getName())) {
                String message = i18n(DuplicateVariableName, "UniqueVariableNames.oneVariable", variableDefinition.getName());
                addError(DuplicateVariableName, variableDefinition.getSourceLocation(), message);
            } else {
                variableNameList.add(variableDefinition.getName());
            }
        }
    }

    // --- SubscriptionUniqueRootField ---
    private void validateSubscriptionUniqueRootField(OperationDefinition operationDef) {
        if (operationDef.getOperation() == SUBSCRIPTION) {
            GraphQLObjectType subscriptionType = validationContext.getSchema().getSubscriptionType();
            FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                    .schema(validationContext.getSchema())
                    .fragments(NodeUtil.getFragmentsByName(validationContext.getDocument()))
                    .variables(CoercedVariables.emptyVariables().toMap())
                    .objectType(subscriptionType)
                    .graphQLContext(validationContext.getGraphQLContext())
                    .build();
            MergedSelectionSet fields = fieldCollector.collectFields(collectorParameters, operationDef.getSelectionSet());
            if (fields.size() > 1) {
                String message = i18n(SubscriptionMultipleRootFields, "SubscriptionUniqueRootField.multipleRootFields", operationDef.getName());
                addError(SubscriptionMultipleRootFields, operationDef.getSourceLocation(), message);
            } else {
                MergedField mergedField = fields.getSubFieldsList().get(0);
                if (isIntrospectionField(mergedField)) {
                    String message = i18n(SubscriptionIntrospectionRootField, "SubscriptionIntrospectionRootField.introspectionRootField", operationDef.getName(), mergedField.getName());
                    addError(SubscriptionIntrospectionRootField, mergedField.getSingleField().getSourceLocation(), message);
                }
            }
        }
    }

    private boolean isIntrospectionField(MergedField field) {
        return field.getName().startsWith("__");
    }

    // --- UniqueObjectFieldName ---
    private void validateUniqueObjectFieldName(ObjectValue objectValue) {
        Set<String> fieldNames = Sets.newHashSetWithExpectedSize(objectValue.getObjectFields().size());
        for (ObjectField field : objectValue.getObjectFields()) {
            String fieldName = field.getName();
            if (fieldNames.contains(fieldName)) {
                String message = i18n(UniqueObjectFieldName, "UniqueObjectFieldName.duplicateFieldName", fieldName);
                addError(UniqueObjectFieldName, objectValue.getSourceLocation(), message);
            } else {
                fieldNames.add(fieldName);
            }
        }
    }

    // --- DeferDirectiveOnRootLevel ---
    private void validateDeferDirectiveOnRootLevel(Directive directive) {
        if (!isExperimentalApiKeyEnabled(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT)) {
            return;
        }
        if (!Directives.DeferDirective.getName().equals(directive.getName())) {
            return;
        }
        GraphQLObjectType mutationType = validationContext.getSchema().getMutationType();
        GraphQLObjectType subscriptionType = validationContext.getSchema().getSubscriptionType();
        GraphQLCompositeType parentType = validationContext.getParentType();
        if (mutationType != null && parentType != null && parentType.getName().equals(mutationType.getName())) {
            String message = i18n(MisplacedDirective, "DeferDirective.notAllowedOperationRootLevelMutation", parentType.getName());
            addError(MisplacedDirective, directive.getSourceLocation(), message);
        } else if (subscriptionType != null && parentType != null && parentType.getName().equals(subscriptionType.getName())) {
            String message = i18n(MisplacedDirective, "DeferDirective.notAllowedOperationRootLevelSubscription", parentType.getName());
            addError(MisplacedDirective, directive.getSourceLocation(), message);
        }
    }

    // --- DeferDirectiveOnValidOperation ---
    private void validateDeferDirectiveOnValidOperation(Directive directive, List<Node> ancestors) {
        if (!isExperimentalApiKeyEnabled(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT)) {
            return;
        }
        if (!Directives.DeferDirective.getName().equals(directive.getName())) {
            return;
        }
        Optional<OperationDefinition> operationDefinition = getOperationDefinition(ancestors);
        if (operationDefinition.isPresent() &&
                SUBSCRIPTION.equals(operationDefinition.get().getOperation()) &&
                !ifArgumentMightBeFalse(directive)) {
            String message = i18n(MisplacedDirective, "IncrementalDirective.notAllowedSubscriptionOperation", directive.getName());
            addError(MisplacedDirective, directive.getSourceLocation(), message);
        }
    }

    private Optional<OperationDefinition> getOperationDefinition(List<Node> ancestors) {
        return ancestors.stream()
                .filter(doc -> doc instanceof OperationDefinition)
                .map(def -> (OperationDefinition) def)
                .findFirst();
    }

    private boolean ifArgumentMightBeFalse(Directive directive) {
        Argument ifArgument = directive.getArgumentsByName().get("if");
        if (ifArgument == null) {
            return false;
        }
        if (ifArgument.getValue() instanceof BooleanValue) {
            return !((BooleanValue) ifArgument.getValue()).isValue();
        }
        return ifArgument.getValue() instanceof VariableReference;
    }

    // --- DeferDirectiveLabel ---
    private void validateDeferDirectiveLabel(Directive directive) {
        if (!isExperimentalApiKeyEnabled(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT) ||
                !Directives.DeferDirective.getName().equals(directive.getName()) ||
                directive.getArguments().size() == 0) {
            return;
        }
        Argument labelArgument = directive.getArgument("label");
        if (labelArgument == null || labelArgument.getValue() instanceof NullValue) {
            return;
        }
        Value labelArgumentValue = labelArgument.getValue();
        if (!(labelArgumentValue instanceof StringValue)) {
            String message = i18n(WrongType, "DeferDirective.labelMustBeStaticString");
            addError(WrongType, directive.getSourceLocation(), message);
        } else {
            String labelValue = ((StringValue) labelArgumentValue).getValue();
            if (labelValue != null && checkedDeferLabels.contains(labelValue)) {
                String message = i18n(DuplicateIncrementalLabel, "IncrementalDirective.uniqueArgument", labelArgument.getName(), directive.getName());
                addError(DuplicateIncrementalLabel, directive.getSourceLocation(), message);
            } else if (labelValue != null) {
                checkedDeferLabels.add(labelValue);
            }
        }
    }

    // --- KnownOperationTypes ---
    private void validateKnownOperationTypes(OperationDefinition operationDefinition) {
        OperationDefinition.Operation documentOperation = operationDefinition.getOperation();
        if (documentOperation == OperationDefinition.Operation.MUTATION
                && validationContext.getSchema().getMutationType() == null) {
            String message = i18n(UnknownOperation, "KnownOperationTypes.noOperation", formatOperation(documentOperation));
            addError(UnknownOperation, operationDefinition.getSourceLocation(), message);
        } else if (documentOperation == OperationDefinition.Operation.SUBSCRIPTION
                && validationContext.getSchema().getSubscriptionType() == null) {
            String message = i18n(UnknownOperation, "KnownOperationTypes.noOperation", formatOperation(documentOperation));
            addError(UnknownOperation, operationDefinition.getSourceLocation(), message);
        }
        // Note: No check for QUERY - a GraphQL schema always has a query type
    }

    private String formatOperation(OperationDefinition.Operation operation) {
        return StringKit.capitalize(operation.name().toLowerCase());
    }

    @Override
    public String toString() {
        return "OperationValidator{" + validationContext + "}";
    }
}
