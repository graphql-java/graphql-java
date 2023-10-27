package graphql.analysis.values;

import graphql.PublicSpi;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputSchemaElement;
import graphql.schema.GraphQLInputValueDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A visitor callback used by {@link ValueTraverser}
 */
@PublicSpi
public interface ValueVisitor {

    /**
     * This magic sentinel value indicates that a value should be removed from a list or object versus being set to null,
     * that is the difference between a value not being present and a value being null
     */
    Object ABSENCE_SENTINEL = new Object() {
        @Override
        public String toString() {
            return "ABSENCE_SENTINEL";
        }
    };

    /**
     * Represents the elements that leads to a value and type
     */
    interface InputElements {

        /**
         * @return then list of input schema elements that lead to an input value.
         */
        List<GraphQLInputSchemaElement> getInputElements();

        /**
         * This is the list of input schema elements that are unwrapped, e.g.
         * {@link GraphQLList} and {@link graphql.schema.GraphQLNonNull} types have been removed
         *
         * @return then list of {@link GraphQLInputValueDefinition} elements that lead to an input value.
         */
        List<GraphQLInputSchemaElement> getUnwrappedInputElements();

        /**
         * This is the last {@link GraphQLInputValueDefinition} that pointed to the value during a callback.  This will
         * be either a {@link graphql.schema.GraphQLArgument} or a {@link GraphQLInputObjectField}
         *
         * @return the last {@link GraphQLInputValueDefinition} that contains this value
         */
        GraphQLInputValueDefinition getLastInputValueDefinition();
    }

    /**
     * This is called when a scalar value is encountered
     *
     * @param coercedValue  the value that is in coerced form
     * @param inputType     the type of scalar
     * @param inputElements the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default @Nullable Object visitScalarValue(@Nullable Object coercedValue, GraphQLScalarType inputType, InputElements inputElements) {
        return coercedValue;
    }

    /**
     * This is called when an enum value is encountered
     *
     * @param coercedValue  the value that is in coerced form
     * @param inputType     the type of enum
     * @param inputElements the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default @Nullable Object visitEnumValue(@Nullable Object coercedValue, GraphQLEnumType inputType, InputElements inputElements) {
        return coercedValue;
    }

    /**
     * This is called when an input object field value is encountered
     *
     * @param coercedValue     the value that is in coerced form
     * @param inputObjectType  the input object type containing the input field
     * @param inputObjectField the input object field
     * @param inputElements    the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default @Nullable Object visitInputObjectFieldValue(@Nullable Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, InputElements inputElements) {
        return coercedValue;
    }

    /**
     * This is called when an input object value is encountered.
     *
     * @param coercedValue    the value that is in coerced form
     * @param inputObjectType the input object type
     * @param inputElements   the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default @Nullable Map<String, Object> visitInputObjectValue(@Nullable Map<String, Object> coercedValue, GraphQLInputObjectType inputObjectType, InputElements inputElements) {
        return coercedValue;
    }

    /**
     * This is called when an input list value is encountered.
     *
     * @param coercedValue  the value that is in coerced form
     * @param listInputType the input list type
     * @param inputElements the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default @Nullable List<Object> visitListValue(@Nullable List<Object> coercedValue, GraphQLList listInputType, InputElements inputElements) {
        return coercedValue;
    }


    /**
     * This is called when a {@link GraphQLArgument} is encountered
     *
     * @param coercedValue    the value that is in coerced form
     * @param graphQLArgument the {@link GraphQLArgument} in play
     * @param inputElements   the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default @Nullable Object visitArgumentValue(@Nullable Object coercedValue, GraphQLArgument graphQLArgument, InputElements inputElements) {
        return coercedValue;
    }


    /**
     * This is called when a {@link GraphQLAppliedDirectiveArgument} is encountered
     *
     * @param coercedValue                    the value that is in coerced form
     * @param graphQLAppliedDirectiveArgument the {@link GraphQLAppliedDirectiveArgument} in play
     * @param inputElements                   the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default @Nullable Object visitAppliedDirectiveArgumentValue(@Nullable Object coercedValue, GraphQLAppliedDirectiveArgument graphQLAppliedDirectiveArgument, InputElements inputElements) {
        return coercedValue;
    }

}
