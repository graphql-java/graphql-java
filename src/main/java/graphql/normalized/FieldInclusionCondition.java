package graphql.normalized;

import graphql.Internal;

import java.util.Map;
import java.util.Objects;

/**
 * Represents an unevaluated skip/include condition on a field or fragment.
 * <p>
 * This captures the directive semantics without evaluating against concrete variable values,
 * making it suitable for caching in a variable-independent normalized operation tree.
 * <p>
 * Conditions compose: a field inside a skipped fragment inherits the fragment's condition
 * AND its own condition. Use {@link #and(FieldInclusionCondition)} to combine.
 */
@Internal
public abstract class FieldInclusionCondition {

    /**
     * The field is always included (no skip/include directives).
     */
    public static final FieldInclusionCondition ALWAYS = new Always();

    /**
     * The field is never included (e.g., @include(if: false) with a literal).
     */
    public static final FieldInclusionCondition NEVER = new Never();

    /**
     * Creates a condition that includes when the named variable is true.
     * Corresponds to @include(if: $var).
     */
    public static FieldInclusionCondition includeIf(String variableName) {
        return new VariableCondition(variableName, false);
    }

    /**
     * Creates a condition that includes when the named variable is false.
     * Corresponds to @skip(if: $var), which means include when NOT $var.
     */
    public static FieldInclusionCondition skipIf(String variableName) {
        return new VariableCondition(variableName, true);
    }

    /**
     * Evaluate this condition against concrete variable values.
     *
     * @param variables the coerced variable values
     * @return true if the field should be included
     */
    public abstract boolean evaluate(Map<String, Object> variables);

    /**
     * Combine this condition with another using AND semantics.
     * A field is only included if both conditions are satisfied.
     */
    public FieldInclusionCondition and(FieldInclusionCondition other) {
        if (this == ALWAYS) {
            return other;
        }
        if (other == ALWAYS) {
            return this;
        }
        if (this == NEVER || other == NEVER) {
            return NEVER;
        }
        return new And(this, other);
    }

    // --- Concrete implementations ---

    private static final class Always extends FieldInclusionCondition {
        @Override
        public boolean evaluate(Map<String, Object> variables) {
            return true;
        }

        @Override
        public String toString() {
            return "ALWAYS";
        }
    }

    private static final class Never extends FieldInclusionCondition {
        @Override
        public boolean evaluate(Map<String, Object> variables) {
            return false;
        }

        @Override
        public String toString() {
            return "NEVER";
        }
    }

    static final class VariableCondition extends FieldInclusionCondition {
        private final String variableName;
        private final boolean negated; // true for @skip(if: $var), false for @include(if: $var)

        VariableCondition(String variableName, boolean negated) {
            this.variableName = Objects.requireNonNull(variableName);
            this.negated = negated;
        }

        public String getVariableName() {
            return variableName;
        }

        public boolean isNegated() {
            return negated;
        }

        @Override
        public boolean evaluate(Map<String, Object> variables) {
            Object value = variables.get(variableName);
            boolean boolVal = value instanceof Boolean && (Boolean) value;
            return negated ? !boolVal : boolVal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VariableCondition)) return false;
            VariableCondition that = (VariableCondition) o;
            return negated == that.negated && variableName.equals(that.variableName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(variableName, negated);
        }

        @Override
        public String toString() {
            return (negated ? "NOT($" : "IF($") + variableName + ")";
        }
    }

    static final class And extends FieldInclusionCondition {
        private final FieldInclusionCondition left;
        private final FieldInclusionCondition right;

        And(FieldInclusionCondition left, FieldInclusionCondition right) {
            this.left = left;
            this.right = right;
        }

        public FieldInclusionCondition getLeft() {
            return left;
        }

        public FieldInclusionCondition getRight() {
            return right;
        }

        @Override
        public boolean evaluate(Map<String, Object> variables) {
            return left.evaluate(variables) && right.evaluate(variables);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof And)) return false;
            And and = (And) o;
            return left.equals(and.left) && right.equals(and.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, right);
        }

        @Override
        public String toString() {
            return "(" + left + " AND " + right + ")";
        }
    }
}
