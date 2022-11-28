package graphql.analysis.values;

import graphql.PublicSpi;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;

import java.util.List;
import java.util.Map;

/**
 * A visitor callback used by {@link ValueTraverser}
 */
@PublicSpi
public interface ValueVisitor {

    /**
     * This is called when a scalar value is encountered
     *
     * @param coercedValue       the value that is in coerced form
     * @param inputType          the type of scalar
     * @param index              if the container of this value was a list, this is the index into the list
     * @param containingElements the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
        return coercedValue;
    }

    /**
     * This is called when an enum value is encountered
     *
     * @param coercedValue       the value that is in coerced form
     * @param inputType          the type of enum
     * @param index              if the container of this value was a list, this is the index into the list
     * @param containingElements the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default Object visitEnumValue(Object coercedValue, GraphQLEnumType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
        return coercedValue;
    }

    /**
     * This is called when an input object field value is encountered
     *
     * @param coercedValue       the value that is in coerced form
     * @param inputObjectType    the input object type containing the input field
     * @param inputObjectField   the input object field
     * @param index              if the container of this value was a list, this is the index into the list
     * @param containingElements the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, int index, List<GraphQLDirectiveContainer> containingElements) {
        return coercedValue;
    }

    /**
     * This is called when an input object value is encountered.
     *
     * @param coercedValue       the value that is in coerced form
     * @param inputObjectType    the input object type
     * @param index              if the container of this value was a list, this is the index into the list
     * @param containingElements the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default Map<String, Object> visitInputObjectValue(Map<String, Object> coercedValue, GraphQLInputObjectType inputObjectType, int index, List<GraphQLDirectiveContainer> containingElements) {
        return coercedValue;
    }

    /**
     * This is called when an input list value is encountered.
     *
     * @param coercedValue       the value that is in coerced form
     * @param listInputType      the input list type
     * @param index              if the container of this value was a list, this is the index into the list
     * @param containingElements the elements that lead to this value and type
     *
     * @return the same value or a new value
     */
    default List<Object> visitListValue(List<Object> coercedValue, GraphQLList listInputType, int index, List<GraphQLDirectiveContainer> containingElements) {
        return coercedValue;
    }
}
