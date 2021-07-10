package graphql.schema;

import graphql.PublicApi;
import graphql.language.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static graphql.Assert.assertNotNull;

/**
 * Used by @{@link GraphQLArgument} and {@link GraphQLInputObjectField} to represent different value states.
 */
@PublicApi
public class InputValueWithState {
    private enum State {
        /**
         * Value was never set aka not provided
         */
        NOT_SET,
        /**
         * The value is an Ast literal
         */
        LITERAL,
        /**
         * The value is an external value
         */
        EXTERNAL_VALUE,
        /**
         * This is only used to preserve backward compatibility (for now): it is a value which is assumed to
         * be already coerced.
         * This will be removed at one point.
         */
        INTERNAL_VALUE

    }

    private final State state;
    private final Object value;

    private InputValueWithState(State state, Object value) {
        this.state = state;
        this.value = value;
    }

    public static final InputValueWithState NOT_SET = new InputValueWithState(State.NOT_SET, null);

    public static InputValueWithState newLiteralValue(@NotNull Value value) {
        assertNotNull(value, () -> "value literal can't be null");
        return new InputValueWithState(State.LITERAL, value);
    }

    public static InputValueWithState newExternalValue(@Nullable Object value) {
        return new InputValueWithState(State.EXTERNAL_VALUE, value);
    }

    public static InputValueWithState newInternalValue(@Nullable Object value) {
        return new InputValueWithState(State.INTERNAL_VALUE, value);
    }

    public @Nullable Object getValue() {
        return value;
    }

    public boolean isNotSet() {
        return state == State.NOT_SET;
    }

    public boolean isSet() {
        return state != State.NOT_SET;
    }

    public boolean isLiteral() {
        return state == State.LITERAL;
    }

    public boolean isExternal() {
        return state == State.EXTERNAL_VALUE;
    }

    public boolean isInternal() {
        return state == State.INTERNAL_VALUE;
    }

    @Override
    public String toString() {
        return "InputValueWithState{" +
                "state=" + state +
                ", value=" + value +
                '}';
    }
}