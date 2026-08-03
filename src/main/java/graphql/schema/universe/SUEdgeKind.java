package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

import static graphql.Assert.assertTrue;

/**
 * The relationship kinds between schema-universe vertices.
 *
 * <p>Each kind has a stable compact code stored in the high eight bits of a packed edge.
 * {@code single} limits the kind to one target per source. {@code uniqueName} allows multiple
 * targets but rejects two targets with the same universe-interned name. {@code ordered} preserves
 * attachment order instead of sorting a kind's targets. Applied directives permit duplicate names
 * because repeatable directive occurrences must remain distinct.</p>
 */
@Internal
@NullMarked
public enum SUEdgeKind {
    QUERY_TYPE(1, true, true, false),
    MUTATION_TYPE(2, true, true, false),
    SUBSCRIPTION_TYPE(3, true, true, false),
    ADDITIONAL_TYPE(4, false, true, false),
    DIRECTIVE_DEFINITION(5, false, true, false),
    FIELD(6, false, true, false),
    ARGUMENT(7, false, true, false),
    TYPE(8, true, true, false),
    IMPLEMENTS(9, false, true, false),
    UNION_MEMBER(10, false, true, false),
    ENUM_VALUE(11, false, true, false),
    INPUT_FIELD(12, false, true, false),
    APPLIED_DIRECTIVE(13, false, false, true),
    WRAPPED_TYPE(15, true, true, false);

    private static final SUEdgeKind[] BY_CODE = new SUEdgeKind[16];

    static {
        for (SUEdgeKind kind : values()) {
            BY_CODE[kind.code] = kind;
        }
    }

    private final int code;
    private final boolean single;
    private final boolean uniqueName;
    private final boolean ordered;

    SUEdgeKind(int code, boolean single, boolean uniqueName, boolean ordered) {
        this.code = code;
        this.single = single;
        this.uniqueName = uniqueName;
        this.ordered = ordered;
    }

    /**
     * Returns the stable packed representation of this kind.
     *
     * @return the packed edge code
     */
    @Internal
    public int getCode() {
        return code;
    }

    /**
     * Reports whether a source may have at most one edge of this kind.
     *
     * @return {@code true} for a single-valued edge kind
     */
    @Internal
    public boolean isSingle() {
        return single;
    }

    /**
     * Reports whether targets of this kind must have distinct names for one source.
     *
     * @return {@code true} when duplicate target names are forbidden
     */
    @Internal
    public boolean isUniqueName() {
        return uniqueName;
    }

    /**
     * Reports whether targets retain their attachment order.
     *
     * @return {@code true} when targets are not sorted within their edge-kind range
     */
    @Internal
    public boolean isOrdered() {
        return ordered;
    }

    /**
     * Resolves a packed edge code.
     *
     * @param code the packed code
     *
     * @return the corresponding edge kind
     */
    @Internal
    public static SUEdgeKind fromCode(int code) {
        assertTrue(
                code > 0 && code < BY_CODE.length && BY_CODE[code] != null,
                "Invalid schema universe edge code %s",
                code);
        return BY_CODE[code];
    }
}
