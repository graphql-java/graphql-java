package graphql.analysis.values;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.PublicApi;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLTypeUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class allows you to traverse a set of input values according to the type system and optional
 * change the values present.
 * <p>
 * If you just want to traverse without changing anything, just return the value presented to you and nothing will change.
 * <p>
 * If you want to change a value, perhaps in the presence of a directive say on the containing element, then return
 * a new value back in your visitor.
 * <p>
 * This class is intended to be used say inside a DataFetcher, allowing you to change the {@link DataFetchingEnvironment#getArguments()}
 * say before further processing.
 * <p>
 * The values passed in are assumed to be valid and coerced.  This classes does not check for non nullness say or the right coerced objects given
 * the type system.  This is assumed to have occurred earlier in the graphql validation phase.  This also means if you are not careful you can undo the
 * validation that has gone before you.  For example, it would be possible to change values that are illegal according to the type system, such as
 * null values for non-nullable types say, so you need to be careful.
 */
@PublicApi
public class ValueTraverser {


    /**
     * This will visit the arguments of a {@link DataFetchingEnvironment} and if the values are changed by the visitor a new environment will be built
     *
     * @param environment the starting data fetching environment
     * @param visitor     the visitor to use
     *
     * @return the same environment if nothing changes or a new one with the {@link DataFetchingEnvironment#getArguments()} changed
     */
    public static DataFetchingEnvironment visitPreOrder(DataFetchingEnvironment environment, ValueVisitor visitor) {
        GraphQLFieldDefinition fieldDefinition = environment.getFieldDefinition();
        Map<String, Object> originalArgs = environment.getArguments();
        Map<String, Object> newArgs = visitPreOrder(originalArgs, fieldDefinition, visitor);
        if (newArgs != originalArgs) {
            return DataFetchingEnvironmentImpl.newDataFetchingEnvironment(environment).arguments(newArgs).build();
        }
        return environment;
    }

    /**
     * This will visit the arguments of a {@link GraphQLFieldDefinition} and if the visitor changes the values, it will return a new set of arguments
     *
     * @param coercedArgumentValues the starting coerced arguments
     * @param fieldDefinition       the field definition
     * @param visitor               the visitor to use
     *
     * @return the same set of arguments if nothing changes or new ones if the visitor changes anything
     */
    public static Map<String, Object> visitPreOrder(Map<String, Object> coercedArgumentValues, GraphQLFieldDefinition fieldDefinition, ValueVisitor visitor) {
        List<GraphQLArgument> fieldArguments = fieldDefinition.getArguments();
        boolean copied = false;
        for (GraphQLArgument fieldArgument : fieldArguments) {
            String key = fieldArgument.getName();
            Object argValue = coercedArgumentValues.get(key);
            if (argValue != null) {
                Object newValue = visitPreOrderImpl(argValue, fieldArgument.getType(), ImmutableList.of(fieldDefinition, fieldArgument), -1, visitor);
                if (newValue != argValue) {
                    if (!copied) {
                        coercedArgumentValues = new LinkedHashMap<>(coercedArgumentValues);
                        copied = true;
                    }
                    coercedArgumentValues.put(key, newValue);
                }
            }
        }
        return coercedArgumentValues;
    }

    /**
     * This will visit a single argument of a {@link GraphQLArgument} and if the visitor changes the value, it will return a new argument
     *
     * @param coercedArgumentValue the starting coerced argument value
     * @param argument             the argument definition
     * @param visitor              the visitor to use
     *
     * @return the same value if nothing changes or a new value if the visitor changes anything
     */
    public static Object visitPreOrder(Object coercedArgumentValue, GraphQLArgument argument, ValueVisitor visitor) {
        return visitPreOrderImpl(coercedArgumentValue, argument.getType(), ImmutableList.of(argument), -1, visitor);
    }

    private static Object visitPreOrderImpl(Object coercedValue, GraphQLInputType startingInputType, List<GraphQLDirectiveContainer> containingElements, int index, ValueVisitor visitor) {
        GraphQLInputType inputType = GraphQLTypeUtil.unwrapNonNullAs(startingInputType);
        if (inputType instanceof GraphQLList) {
            return visitListValue(coercedValue, (GraphQLList) inputType, index, containingElements, visitor);
        } else if (inputType instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) inputType;
            return visitObjectValue(coercedValue, inputObjectType, index, containingElements, visitor);
        } else if (inputType instanceof GraphQLScalarType) {
            return visitor.visitScalarValue(coercedValue, (GraphQLScalarType) inputType, index, containingElements);
        } else if (inputType instanceof GraphQLEnumType) {
            return visitor.visitEnumValue(coercedValue, (GraphQLEnumType) inputType, index, containingElements);
        } else {
            return Assert.assertShouldNeverHappen("ValueTraverser can only be called on full materialised schemas");
        }
    }

    private static Object visitObjectValue(Object coercedValue, GraphQLInputObjectType inputObjectType, int index, List<GraphQLDirectiveContainer> containingElements, ValueVisitor visitor) {
        if (coercedValue != null) {
            Assert.assertTrue(coercedValue instanceof Map, () -> "A input object type MUST have an Map<String,Object> value");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) coercedValue;
        Map<String, Object> newMap = visitor.visitInputObjectValue(map, inputObjectType, index, containingElements);
        if (newMap != null) {
            List<GraphQLDirectiveContainer> containersWithObject = pushElement(containingElements, inputObjectType);
            boolean copied = false;
            for (Map.Entry<String, Object> entry : newMap.entrySet()) {
                GraphQLInputObjectField inputField = inputObjectType.getField(entry.getKey());
                /// should we assert if the map contain a key that's not a field ?
                if (inputField != null) {
                    Object newSubValue = visitor.visitInputObjectFieldValue(entry.getValue(), inputObjectType, inputField, index, containersWithObject);
                    if (newSubValue != entry.getValue()) {
                        if (!copied) {
                            newMap = new LinkedHashMap<>(newMap);
                            copied = true;
                        }
                        newMap.put(entry.getKey(), newSubValue);
                    }
                    List<GraphQLDirectiveContainer> containersWithField = pushElement(containingElements, inputField);
                    newSubValue = visitPreOrderImpl(newSubValue, inputField.getType(), containersWithField, -1, visitor);
                    if (newSubValue != entry.getValue()) {
                        if (!copied) {
                            newMap = new LinkedHashMap<>(newMap);
                            copied = true;
                        }
                        newMap.put(entry.getKey(), newSubValue);
                    }
                }
            }
            return newMap;
        } else {
            return null;
        }
    }

    private static Object visitListValue(Object coercedValue, GraphQLList listInputType, int index, List<GraphQLDirectiveContainer> containingElements, ValueVisitor visitor) {
        if (coercedValue != null) {
            Assert.assertTrue(coercedValue instanceof List, () -> "A list type MUST have an List value");
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) coercedValue;
        List<Object> newList = visitor.visitListValue(list, listInputType, index, containingElements);
        if (newList != null) {
            GraphQLInputType inputType = GraphQLTypeUtil.unwrapOneAs(listInputType);
            ImmutableList.Builder<Object> copiedList = null;
            int i = 0;
            for (Object subValue : newList) {
                Object newSubValue = visitPreOrderImpl(subValue, inputType, containingElements, i, visitor);
                if (copiedList != null) {
                    copiedList.add(newSubValue);
                } else if (newSubValue != subValue) {
                    // go into copy mode because something has changed
                    // copy previous values up to this point
                    copiedList = ImmutableList.builder();
                    for (int j = 0; j < i; j++) {
                        copiedList.add(newList.get(j));
                    }
                    copiedList.add(newSubValue);
                }
                i++;
            }
            if (copiedList != null) {
                return copiedList.build();
            } else {
                return newList;
            }
        } else {
            return null;
        }
    }

    private static List<GraphQLDirectiveContainer> pushElement(List<GraphQLDirectiveContainer> containingElements, GraphQLDirectiveContainer element) {
        return ImmutableList.<GraphQLDirectiveContainer>builder().addAll(containingElements).add(element).build();
    }
}
