package graphql.schema.validation;

import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;

/**
 * Validates that every input object type can be provided a finite value.
 */
@Internal
public class InputObjectHasUnbreakableCycle extends GraphQLTypeVisitorStub {

    /**
     * The fields on each input object that lead to another input object and can keep a cycle going.
     * For example, given these types:
     *     input A @oneOf { b: B, value: Int }
     *     input B { value: Int }
     * This contains only A.b for A.
     */
    private final Map<GraphQLInputObjectType, List<GraphQLInputObjectField>> targetFields = new LinkedHashMap<>();

    /**
     * The input object reached by each target field.
     * For example, given these types:
     *     input A @oneOf { b: B }
     *     input B { value: Int }
     * A.b maps to B.
     */
    private final Map<GraphQLInputObjectField, GraphQLInputObjectType> fieldTargets = new LinkedHashMap<>();

    /**
     * The input objects that point to a given input object.
     * For example, given these types:
     *     input A { b: B! }
     *     input B { value: Int }
     * B lists A as a dependent.
     */
    private final Map<GraphQLInputObjectType, List<GraphQLInputObjectType>> dependents = new LinkedHashMap<>();

    /** The total number of fields on each input object, including scalar, enum, and list fields. */
    private final Map<GraphQLInputObjectType, Integer> fieldCounts = new LinkedHashMap<>();

    /**
     * For each regular input object, the number of required input object fields
     * that are not yet known to have a finite value.
     */
    private final Map<GraphQLInputObjectType, Integer> unresolvedTargetCounts = new LinkedHashMap<>();

    /** The input objects for which a finite value is known to exist. */
    private final Set<GraphQLInputObjectType> typesWithFiniteValues = new LinkedHashSet<>();

    /** Input objects newly known to have a finite value, but whose dependents have not yet been updated. */
    private final Deque<GraphQLInputObjectType> typesToPropagate = new ArrayDeque<>();

    /**
     * True after this validator has checked the whole schema.
     * The schema visitor calls this validator once for each input object, but the first call checks every input
     * object in the schema. This prevents the same work from running again on later calls.
     */
    private boolean complete;

    @Override
    public TraversalControl visitGraphQLInputObjectType(
            GraphQLInputObjectType inputObjectType,
            TraverserContext<GraphQLSchemaElement> context
    ) {
        // This validator runs once per schema. Continue if we've already run.
        if (complete) {
            return TraversalControl.CONTINUE;
        }
        complete = true;

        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        List<GraphQLInputObjectType> inputObjectTypes = getInputObjectTypes(schema);
        detectInputObjectNonFiniteValues(inputObjectTypes, errorCollector);
        return TraversalControl.CONTINUE;
    }

    private List<GraphQLInputObjectType> getInputObjectTypes(GraphQLSchema schema) {
        return ImmutableKit.filterAndMap(
                schema.getTypeMap().values(),
                GraphQLInputObjectType.class::isInstance,
                GraphQLInputObjectType.class::cast
        );
    }

    private void detectInputObjectNonFiniteValues(
            List<GraphQLInputObjectType> inputObjectTypes,
            SchemaValidationErrorCollector errorCollector
    ) {
        /*
         * Finds input objects that cannot be given a finite value.
         *
         * Start with types known to have a finite value, then repeatedly mark types that become finite because of them.
         * Once no more types can be marked, report cycles among the remaining types.
         */

        // Prepare a place to store the state of each input object.
        initializeInputObjectStates(inputObjectTypes);

        // Record the input object fields that can keep a cycle going.
        recordFiniteValueTargets(inputObjectTypes);

        // Start with the input objects that clearly have a finite value.
        initializeFiniteValueWorklist(inputObjectTypes);

        // Use those finite values to find other input objects with finite values.
        propagateFiniteValues();

        // Report cycles among the input objects that are still not known to have a finite value.
        reportUnbreakableCycles(inputObjectTypes, errorCollector);
    }

    private void initializeInputObjectStates(List<GraphQLInputObjectType> inputObjectTypes) {
        for (GraphQLInputObjectType inputObjectType : inputObjectTypes) {
            targetFields.put(inputObjectType, new ArrayList<>());
            dependents.put(inputObjectType, new ArrayList<>());
            unresolvedTargetCounts.put(inputObjectType, 0);
        }
    }

    private void recordFiniteValueTargets(List<GraphQLInputObjectType> inputObjectTypes) {
        for (GraphQLInputObjectType inputObjectType : inputObjectTypes) {
            List<GraphQLInputObjectField> fields = inputObjectType.getFieldDefinitions();
            fieldCounts.put(inputObjectType, fields.size());
            for (GraphQLInputObjectField field : fields) {
                recordFiniteValueTarget(inputObjectType, field);
            }
        }
    }

    private void recordFiniteValueTarget(
            GraphQLInputObjectType inputObjectType,
            GraphQLInputObjectField field
    ) {
        GraphQLInputObjectType target = getFiniteValueTarget(inputObjectType, field.getType());
        if (target == null) {
            return;
        }

        targetFields.get(inputObjectType).add(field);
        fieldTargets.put(field, target);
        List<GraphQLInputObjectType> targetDependents = dependents.get(target);
        if (targetDependents == null) {
            return;
        }
        targetDependents.add(inputObjectType);
    }

    private void initializeFiniteValueWorklist(List<GraphQLInputObjectType> inputObjectTypes) {
        for (GraphQLInputObjectType inputObjectType : inputObjectTypes) {
            List<GraphQLInputObjectField> targets = targetFields.get(inputObjectType);
            if (inputObjectType.isOneOf()) {
                initializeOneOfFiniteValue(inputObjectType, targets);
                continue;
            }

            unresolvedTargetCounts.put(inputObjectType, targets.size());
            if (targets.isEmpty()) {
                markInputObjectHasFiniteValue(inputObjectType);
            }
        }
    }

    private void initializeOneOfFiniteValue(
            GraphQLInputObjectType inputObjectType,
            List<GraphQLInputObjectField> targets
    ) {
        int fieldCount = fieldCounts.get(inputObjectType);
        if (fieldCount == 0 || targets.size() < fieldCount) {
            markInputObjectHasFiniteValue(inputObjectType);
        }
    }

    private void propagateFiniteValues() {
        while (!typesToPropagate.isEmpty()) {
            GraphQLInputObjectType finiteType = typesToPropagate.removeLast();
            for (GraphQLInputObjectType dependent : dependents.get(finiteType)) {
                propagateFiniteValueToDependent(dependent);
            }
        }
    }

    private void propagateFiniteValueToDependent(GraphQLInputObjectType dependent) {
        if (typesWithFiniteValues.contains(dependent)) {
            return;
        }
        if (dependent.isOneOf()) {
            markInputObjectHasFiniteValue(dependent);
            return;
        }

        int unresolvedTargetCount = unresolvedTargetCounts.get(dependent) - 1;
        unresolvedTargetCounts.put(dependent, unresolvedTargetCount);
        if (unresolvedTargetCount == 0) {
            markInputObjectHasFiniteValue(dependent);
        }
    }

    private void markInputObjectHasFiniteValue(GraphQLInputObjectType inputObjectType) {
        if (!typesWithFiniteValues.add(inputObjectType)) {
            return;
        }
        typesToPropagate.add(inputObjectType);
    }

    @Nullable
    private GraphQLInputObjectType getFiniteValueTarget(
            GraphQLInputObjectType inputObjectType,
            GraphQLInputType fieldType
    ) {
        if (inputObjectType.isOneOf()) {
            if (fieldType instanceof GraphQLInputObjectType) {
                return (GraphQLInputObjectType) fieldType;
            }
            return null;
        }
        if (!(fieldType instanceof GraphQLNonNull)) {
            return null;
        }

        GraphQLType nullableType = ((GraphQLNonNull) fieldType).getWrappedType();
        if (nullableType instanceof GraphQLInputObjectType) {
            return (GraphQLInputObjectType) nullableType;
        }
        return null;
    }

    private void reportUnbreakableCycles(
            List<GraphQLInputObjectType> inputObjectTypes,
            SchemaValidationErrorCollector errorCollector
    ) {
        Set<GraphQLInputObjectType> visitedTypes = new LinkedHashSet<>();
        List<String> fieldPath = new ArrayList<>();
        Map<GraphQLInputObjectType, Integer> fieldPathIndexByType = new LinkedHashMap<>();
        for (GraphQLInputObjectType inputObjectType : inputObjectTypes) {
            if (typesWithFiniteValues.contains(inputObjectType)) {
                continue;
            }
            reportCycleRecursive(
                    inputObjectType,
                    visitedTypes,
                    fieldPath,
                    fieldPathIndexByType,
                    errorCollector
            );
        }
    }

    private void reportCycleRecursive(
            GraphQLInputObjectType inputObjectType,
            Set<GraphQLInputObjectType> visitedTypes,
            List<String> fieldPath,
            Map<GraphQLInputObjectType, Integer> fieldPathIndexByType,
            SchemaValidationErrorCollector errorCollector
    ) {
        if (!visitedTypes.add(inputObjectType)) {
            return;
        }
        fieldPathIndexByType.put(inputObjectType, fieldPath.size());

        for (GraphQLInputObjectField field : targetFields.get(inputObjectType)) {
            reportTargetCycle(
                    inputObjectType,
                    field,
                    visitedTypes,
                    fieldPath,
                    fieldPathIndexByType,
                    errorCollector
            );
        }
        fieldPathIndexByType.remove(inputObjectType);
    }

    private void reportTargetCycle(
            GraphQLInputObjectType inputObjectType,
            GraphQLInputObjectField field,
            Set<GraphQLInputObjectType> visitedTypes,
            List<String> fieldPath,
            Map<GraphQLInputObjectType, Integer> fieldPathIndexByType,
            SchemaValidationErrorCollector errorCollector
    ) {
        GraphQLInputObjectType target = fieldTargets.get(field);
        if (!unresolvedTargetCounts.containsKey(target) || typesWithFiniteValues.contains(target)) {
            return;
        }

        Integer cycleIndex = fieldPathIndexByType.get(target);
        fieldPath.add(inputObjectType.getName() + "." + field.getName());
        if (cycleIndex == null) {
            reportCycleRecursive(target, visitedTypes, fieldPath, fieldPathIndexByType, errorCollector);
        } else {
            addCycleError(target, cycleIndex, fieldPath, errorCollector);
        }
        fieldPath.remove(fieldPath.size() - 1);
    }

    private void addCycleError(
            GraphQLInputObjectType inputObjectType,
            int cycleIndex,
            List<String> fieldPath,
            SchemaValidationErrorCollector errorCollector
    ) {
        List<String> cyclePath = fieldPath.subList(cycleIndex, fieldPath.size());
        String message = format(
                "Input Object %s cannot be provided a finite value because it references itself through fields: %s.",
                inputObjectType.getName(),
                String.join(", ", cyclePath)
        );
        errorCollector.addError(
                new SchemaValidationError(SchemaValidationErrorType.UnbrokenInputCycle, message)
        );
    }
}
