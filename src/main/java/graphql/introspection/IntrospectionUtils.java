package graphql.introspection;

import graphql.Assert;
import graphql.PublicApi;
import graphql.execution.MergedField;
import graphql.execution.nextgen.FieldSubSelection;
import graphql.language.Document;
import graphql.language.NamedNode;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * This class provides several utilities for testing whether a GraphQL operation represents some form of an
 * introspection query.  The varying methods can have different affects on performance based on the method used for
 * testing as well as how frequent the tests are performed.
 */
@PublicApi
public final class IntrospectionUtils {
    private IntrospectionUtils() {
    }

    public enum ScanType {
        // Note:  The scan type applies only to TESTED fields.  The algorithms below do not necessary test every
        // field in the operation.  Most of the time, top-level fields are examined and that is enough to deterimine
        // introspection.

        // All tested fields in the operation must be system fields
        ALL,
        // There must be a mixture of both system and non-system for the tested fields in the operation
        MIXED,
        // Any tested field in the operation can be a system field
        ANY
    }

    public static final String SYSTEM_FIELD_PREFIX = "__";
    public static final String INTROSPECTION_SCHEMA_FIELD_NAME = SYSTEM_FIELD_PREFIX + "schema";

    /**
     * Tests whether the provided {@code operationName} is equal to the traditional operation name used to represent the
     * GraphQL introspection query.
     *
     * Note:  This method makes no claim that the operation being executed is actually the introspection query, only
     * that its name matches the traditional name.  While this could lead to false-positives, this method is meant
     * to act as a quick and more performant check rather than some of the other versions.  If you don't have access
     * to the GraphQL document, need something to check for introspection, and are ok with potential false-positives,
     * this is a suitable choice.
     *
     * @param operationName The operation name to test.
     * @return {@code true} if the operation name equals {@link IntrospectionQuery#INTROSPECTION_QUERY_NAME}, false
     *   otherwise
     */
    public static boolean isIntrospectionOperationName(final String operationName) {
        return IntrospectionQuery.INTROSPECTION_QUERY_NAME.equals(operationName);
    }

    /**
     * Tests whether the provided {@code query} represents a GraphQL introspection query or not.
     *
     * Note:  This method scans for "__schema" in the query to determine if this is an introspection query.  This
     * method makes no claim that the query is a "pure" introspection query.  That is, solely uses schema/system
     * type fields.  The query could still be a mixture of fields.  It should be noted that this method can perform
     * poorly.  Its performance is directly tied to the length of the query and where in the query __schema is
     * found, if at all.  For this reason, this method is not a suitable choice when it would be invoked in high
     * volume instances.
     *
     * @param query Query to test
     * @return {@code true} if the query contains {@link IntrospectionUtils#INTROSPECTION_SCHEMA_FIELD_NAME}, false
     *   otherwise.
     */
    public static boolean isIntrospectionQuery(final String query) {
        return (null != query) && !query.isEmpty() && query.contains(IntrospectionUtils.INTROSPECTION_SCHEMA_FIELD_NAME);
    }

    /**
     * Tests whether the provided {@code document} represents a GraphQL introspection query or not.
     *
     * Note:  The method searches for system fields using the system field prefix, "__", by finding the
     *   {@code OperationDefinition} associated with {@code operationName} and testing its selection set.
     *
     * Note 2:  The method also provides different types of scanning for introspection.  The ALL scan type ensures all
     * tested fields are system fields, the MIXED type checks if some but not all tested fields are system fields, and
     * the ANY type checks if at least one tested field is a system field.  The scan type can have an effect on
     * performance.
     *
     * @param document The GraphQL document to test
     * @param operationName The name of the operation to be tested
     * @param scanType The type of scan used to test for introspection
     * @return {@code true} if the selection set in the {@code OperationDefinition} retrieved from the {@code Document}
     * based on {@code operationName} represents an introspection query, {@code false} otherwise
     */
    public static boolean isIntrospectionDocument(final Document document,
                                                  final String operationName,
                                                  final ScanType scanType) {
        Assert.assertNotNull(scanType);
        if (null == document || null == operationName || operationName.isEmpty()) {
            return false;
        }

        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        OperationDefinition operationDefinition = getOperationResult.operationDefinition;

        return isIntrospectionOperationDefinition(operationDefinition, scanType);
    }

    /**
     * Tests whether the provided {@code operationDefinition} represents a GraphQL introspection query or not.
     *
     * Note:  The method searches for system fields using the system field prefix, "__", by testing the selection set
     * of the {@code operationDefinition}.
     *
     * Note 2:  The method also provides different types of scanning for introspection.  The ALL scan type ensures all
     * tested fields are system fields, the MIXED type checks if some but not all tested fields are system fields, and
     * the ANY type checks if at least one tested field is a system field.  The scan type can have an effect on
     * performance.
     *
     * @param operationDefinition The GraphQL {@code OperationDefinition} to test
     * @param scanType The type of scan used to test for introspection
     * @return {@code true} if this {@code OperationDefinition} represents an introspection query, {@code false}
     * otherwise
     */
    public static boolean isIntrospectionOperationDefinition(final OperationDefinition operationDefinition,
                                                             final ScanType scanType) {
        Assert.assertNotNull(scanType);
        if (null == operationDefinition) {
            return false;
        }

        final SelectionSet selectionSet = operationDefinition.getSelectionSet();

        switch (scanType) {
            case ANY:
                return selectionSet.getSelections().stream().anyMatch(IntrospectionUtils::isSystemSelection);
            case MIXED:
                final Set<Boolean> fieldTypes = new HashSet<>();
                return selectionSet.getSelections().stream().anyMatch(selection -> {
                    fieldTypes.add(isSystemSelection(selection));
                    return 1 < fieldTypes.size();
                });
            case ALL:
            default:
                return selectionSet.getSelections().stream().allMatch(IntrospectionUtils::isSystemSelection);
        }
    }

    /**
     * Tests whether the provided {@code FieldSubSelection} has GraphQL introspection/system fields or not.
     *
     * Note:  The method searches the {@code FieldSubSelection} sub field values for system fields using the system
     * field prefix, "__".
     *
     * Note 2:  The method also provides different types of scanning for introspection.  The ALL scan type ensures all
     * tested fields are system fields, the MIXED type checks if some but not all tested fields are system fields, and
     * the ANY type checks if at least one tested field is a system field.  The scan type can have an effect on
     * performance.
     *
     * @param fieldSubSelection The field sub selection to test
     * @param scanType The type of scan used to test for introspection
     * @return {@code true} if this {@code FieldSubSelection} represents an introspection query, {@code false} otherwise
     */
    public static boolean isIntrospectionFieldSubSelection(final FieldSubSelection fieldSubSelection,
                                                           final ScanType scanType) {
        Assert.assertNotNull(scanType);
        if (null == fieldSubSelection) {
            return false;
        }

        // Use the values instead of keys to account for aliasing
        final Collection<MergedField> fieldValues = fieldSubSelection.getSubFields().values();

        switch (scanType) {
            case ANY:
                return fieldValues.stream().anyMatch(IntrospectionUtils::isSystemMergedField);
            case MIXED:
                final Set<Boolean> fieldTypes = new HashSet<>();
                return fieldValues.stream().anyMatch(mergedField -> {
                    fieldTypes.add(isSystemMergedField(mergedField));
                    return 1 < fieldTypes.size();
                });
            case ALL:
            default:
                return fieldValues.stream().allMatch(IntrospectionUtils::isSystemMergedField);
        }
    }

    private static boolean isSystemSelection(final Selection selection) {
        if (selection instanceof NamedNode) {
            final NamedNode node = (NamedNode) selection;
            return node.getName().startsWith(SYSTEM_FIELD_PREFIX);
        }

        return false;
    }

    private static boolean isSystemMergedField(final MergedField mergedField) {
        return mergedField.getName().startsWith(IntrospectionUtils.SYSTEM_FIELD_PREFIX);
    }
}
