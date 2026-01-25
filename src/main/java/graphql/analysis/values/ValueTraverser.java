package graphql.analysis.values;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputSchemaElement;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInputValueDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLTypeUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.analysis.values.ValueVisitor.ABSENCE_SENTINEL;

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
@NullMarked
public class ValueTraverser {

    private static class InputElements implements ValueVisitor.InputElements {

        private final ImmutableList<GraphQLInputSchemaElement> inputElements;
        private final List<GraphQLInputSchemaElement> unwrappedInputElements;
        private final @Nullable GraphQLInputValueDefinition lastElement;

        private InputElements(GraphQLInputSchemaElement startElement) {
            this.inputElements = ImmutableList.of(startElement);
            this.unwrappedInputElements = ImmutableList.of(startElement);
            this.lastElement = startElement instanceof GraphQLInputValueDefinition ? (GraphQLInputValueDefinition) startElement : null;
        }

        private InputElements(ImmutableList<GraphQLInputSchemaElement> inputElements) {
            this.inputElements = inputElements;
            this.unwrappedInputElements = ImmutableKit.filter(inputElements,
                    it -> !(it instanceof GraphQLNonNull || it instanceof GraphQLList));

            List<GraphQLInputValueDefinition> inputValDefs = ImmutableKit.filterAndMap(unwrappedInputElements,
                    it -> it instanceof GraphQLInputValueDefinition,
                    GraphQLInputValueDefinition.class::cast);
            this.lastElement = inputValDefs.isEmpty() ? null : inputValDefs.get(inputValDefs.size() - 1);
        }


        private InputElements push(GraphQLInputSchemaElement inputElement) {
            ImmutableList<GraphQLInputSchemaElement> newSchemaElements = ImmutableList.<GraphQLInputSchemaElement>builder()
                    .addAll(inputElements).add(inputElement).build();
            return new InputElements(newSchemaElements);
        }

        @Override
        public List<GraphQLInputSchemaElement> getInputElements() {
            return inputElements;
        }

        public List<GraphQLInputSchemaElement> getUnwrappedInputElements() {
            return unwrappedInputElements;
        }

        @Override
        public @Nullable GraphQLInputValueDefinition getLastInputValueDefinition() {
            return lastElement;
        }
    }

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
            InputElements inputElements = new InputElements(fieldArgument);
            Object newValue = visitor.visitArgumentValue(argValue, fieldArgument, inputElements);
            if (hasChanged(newValue, argValue)) {
                if (!copied) {
                    coercedArgumentValues = new LinkedHashMap<>(coercedArgumentValues);
                    copied = true;
                }
                setNewValue(coercedArgumentValues, key, newValue);
            }
            if (newValue != ABSENCE_SENTINEL) {
                newValue = visitPreOrderImpl(argValue, fieldArgument.getType(), inputElements, visitor);
                if (hasChanged(newValue, argValue)) {
                    if (!copied) {
                        coercedArgumentValues = new LinkedHashMap<>(coercedArgumentValues);
                        copied = true;
                    }
                    setNewValue(coercedArgumentValues, key, newValue);
                }
            }
        }
        return coercedArgumentValues;
    }

    /**
     * This will visit a single argument of a {@link GraphQLArgument} and if the visitor changes the value, it will return a new argument value
     * <p>
     * Note you cannot return the ABSENCE_SENTINEL from this method as its makes no sense to be somehow make the argument disappear.  Use
     * {@link #visitPreOrder(Map, GraphQLFieldDefinition, ValueVisitor)} say to remove arguments in the fields map of arguments.
     *
     * @param coercedArgumentValue the starting coerced argument value
     * @param argument             the argument definition
     * @param visitor              the visitor to use
     *
     * @return the same value if nothing changes or a new value if the visitor changes anything
     */
    public static @Nullable Object visitPreOrder(@Nullable Object coercedArgumentValue, GraphQLArgument argument, ValueVisitor visitor) {
        InputElements inputElements = new InputElements(argument);
        @Nullable Object newValue = visitor.visitArgumentValue(coercedArgumentValue, argument, inputElements);
        if (newValue == ABSENCE_SENTINEL) {
            assertShouldNeverHappen("It makes no sense to return the ABSENCE_SENTINEL during the visitPreOrder GraphQLArgument method");
        }
        newValue = visitPreOrderImpl(newValue, argument.getType(), inputElements, visitor);
        if (newValue == ABSENCE_SENTINEL) {
            assertShouldNeverHappen("It makes no sense to return the ABSENCE_SENTINEL during the visitPreOrder GraphQLArgument method");
        }
        return newValue;
    }

    /**
     * This will visit a single argument of a {@link GraphQLAppliedDirective} and if the visitor changes the value, it will return a new argument value
     * <p>
     * Note you cannot return the ABSENCE_SENTINEL from this method as its makes no sense to be somehow make the argument disappear.
     *
     * @param coercedArgumentValue the starting coerced argument value
     * @param argument             the applied argument
     * @param visitor              the visitor to use
     *
     * @return the same value if nothing changes or a new value if the visitor changes anything
     */
    public static @Nullable Object visitPreOrder(@Nullable Object coercedArgumentValue, GraphQLAppliedDirectiveArgument argument, ValueVisitor visitor) {
        InputElements inputElements = new InputElements(argument);
        @Nullable Object newValue = visitor.visitAppliedDirectiveArgumentValue(coercedArgumentValue, argument, inputElements);
        if (newValue == ABSENCE_SENTINEL) {
            assertShouldNeverHappen("It makes no sense to return the ABSENCE_SENTINEL during the visitPreOrder GraphQLAppliedDirectiveArgument method");
        }
        newValue = visitPreOrderImpl(newValue, argument.getType(), inputElements, visitor);
        if (newValue == ABSENCE_SENTINEL) {
            assertShouldNeverHappen("It makes no sense to return the ABSENCE_SENTINEL during the visitPreOrder GraphQLAppliedDirectiveArgument method");
        }
        return newValue;
    }

    private static @Nullable Object visitPreOrderImpl(@Nullable Object coercedValue, GraphQLInputType startingInputType, InputElements containingElements, ValueVisitor visitor) {
        if (startingInputType instanceof GraphQLNonNull) {
            containingElements = containingElements.push(startingInputType);
        }
        GraphQLInputType inputType = GraphQLTypeUtil.unwrapNonNullAs(startingInputType);
        containingElements = containingElements.push(inputType);
        if (inputType instanceof GraphQLList) {
            return visitListValue(coercedValue, (GraphQLList) inputType, containingElements, visitor);
        } else if (inputType instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) inputType;
            return visitObjectValue(coercedValue, inputObjectType, containingElements, visitor);
        } else if (inputType instanceof GraphQLScalarType) {
            return visitor.visitScalarValue(coercedValue, (GraphQLScalarType) inputType, containingElements);
        } else if (inputType instanceof GraphQLEnumType) {
            return visitor.visitEnumValue(coercedValue, (GraphQLEnumType) inputType, containingElements);
        } else {
            return assertShouldNeverHappen("ValueTraverser can only be called on full materialised schemas");
        }
    }

    private static @Nullable Object visitObjectValue(@Nullable Object coercedValue, GraphQLInputObjectType inputObjectType, InputElements containingElements, ValueVisitor visitor) {
        if (coercedValue != null) {
            assertTrue(coercedValue instanceof Map, "A input object type MUST have an Map<String,Object> value");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) coercedValue;
        Map<String, Object> newMap = visitor.visitInputObjectValue(map, inputObjectType, containingElements);
        if (newMap == ABSENCE_SENTINEL) {
            return ABSENCE_SENTINEL;
        }
        if (newMap != null) {
            boolean copied = false;
            for (Map.Entry<String, Object> entry : newMap.entrySet()) {
                String key = entry.getKey();
                GraphQLInputObjectField inputField = inputObjectType.getField(key);
                /// should we assert if the map contain a key that's not a field ?
                if (inputField != null) {
                    InputElements inputElementsWithField = containingElements.push(inputField);
                    Object newValue = visitor.visitInputObjectFieldValue(entry.getValue(), inputObjectType, inputField, inputElementsWithField);
                    if (hasChanged(newValue, entry.getValue())) {
                        if (!copied) {
                            newMap = new LinkedHashMap<>(newMap);
                            copied = true;
                        }
                        setNewValue(newMap, key, newValue);
                    }
                    // if the value has gone - then we cant descend into it
                    if (newValue != ABSENCE_SENTINEL) {
                        newValue = visitPreOrderImpl(newValue, inputField.getType(), inputElementsWithField, visitor);
                        if (hasChanged(newValue, entry.getValue())) {
                            if (!copied) {
                                newMap = new LinkedHashMap<>(newMap);
                                copied = true;
                            }
                            setNewValue(newMap, key, newValue);
                        }
                    }
                }
            }
            return newMap;
        } else {
            return null;
        }
    }

    private static @Nullable Object visitListValue(@Nullable Object coercedValue, GraphQLList listInputType, InputElements containingElements, ValueVisitor visitor) {
        if (coercedValue != null) {
            assertTrue(coercedValue instanceof List, "A list type MUST have an List value");
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) coercedValue;
        List<Object> newList = visitor.visitListValue(list, listInputType, containingElements);
        if (newList == ABSENCE_SENTINEL) {
            return ABSENCE_SENTINEL;
        }
        if (newList != null) {
            GraphQLInputType inputType = GraphQLTypeUtil.unwrapOneAs(listInputType);
            ImmutableList.Builder<Object> copiedList = null;
            int i = 0;
            for (Object subValue : newList) {
                @Nullable Object newValue = visitPreOrderImpl(subValue, inputType, containingElements, visitor);
                if (copiedList != null) {
                    // ImmutableList doesn't support null values. We only add non-null and non-ABSENCE values
                    if (newValue != ABSENCE_SENTINEL && newValue != null) {
                        copiedList.add(newValue);
                    }
                } else if (hasChanged(newValue, subValue)) {
                    // go into copy mode because something has changed
                    // copy previous values up to this point
                    copiedList = ImmutableList.builder();
                    for (int j = 0; j < i; j++) {
                        copiedList.add(newList.get(j));
                    }
                    // ImmutableList doesn't support null values. We only add non-null and non-ABSENCE values
                    if (newValue != ABSENCE_SENTINEL && newValue != null) {
                        copiedList.add(newValue);
                    }
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

    private static boolean hasChanged(@Nullable Object newValue, @Nullable Object oldValue) {
        return newValue != oldValue || newValue == ABSENCE_SENTINEL;
    }

    private static void setNewValue(Map<String, Object> newMap, String key, @Nullable Object newValue) {
        if (newValue == ABSENCE_SENTINEL) {
            newMap.remove(key);
        } else {
            newMap.put(key, newValue);
        }
    }

}
