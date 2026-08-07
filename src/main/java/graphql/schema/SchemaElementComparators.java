package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.Objects;

/**
 * Common comparators for schema-neutral elements.
 */
@ExperimentalApi
@NullMarked
public final class SchemaElementComparators {

    private SchemaElementComparators() {
    }

    /**
     * @return a comparator that preserves the current order
     */
    public static Comparator<SchemaElement> asIsOrder() {
        return (first, second) -> 0;
    }

    /**
     * @return a comparator that orders named elements by ascending name
     */
    public static Comparator<SchemaElement> byNameAsc() {
        return Comparator.comparing(
                SchemaElementComparators::elementName);
    }

    /**
     * @return the default grouped schema-printer ordering
     */
    public static Comparator<SchemaElement> sensibleGroupedOrder() {
        return Comparator
                .comparingInt(SchemaElementComparators::topLevelRank)
                .thenComparing(SchemaElementComparators::elementName);
    }

    private static String elementName(SchemaElement element) {
        element = unwrapElement(element);
        if (element instanceof SchemaNamedElement) {
            return ((SchemaNamedElement) element).getName();
        }
        return Objects.toString(element);
    }

    private static int topLevelRank(SchemaElement element) {
        element = unwrapElement(element);
        if (element instanceof SchemaDirective) {
            return 1;
        }
        if (element instanceof SchemaInterface) {
            return 2;
        }
        if (element instanceof SchemaUnion) {
            return 3;
        }
        if (element instanceof SchemaObject) {
            return 4;
        }
        if (element instanceof SchemaEnum) {
            return 5;
        }
        if (element instanceof SchemaScalar) {
            return 6;
        }
        if (element instanceof SchemaInputObject) {
            return 7;
        }
        return 0;
    }

    private static SchemaElement unwrapElement(SchemaElement element) {
        if (element instanceof SchemaType) {
            return GraphQLTypeUtil.unwrapAll((SchemaType) element);
        }
        return element;
    }
}
