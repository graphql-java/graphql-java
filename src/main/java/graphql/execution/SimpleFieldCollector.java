package graphql.execution;


import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.execution.TypeFromAST.getTypeFromAST;

public class SimpleFieldCollector implements FieldCollector {
    private static final Logger log = LoggerFactory.getLogger(SimpleFieldCollector.class);

    private final FieldNodeFilter fieldNodeFilter;

    //
    // private final SchemaUtil schemaUtil = new SchemaUtil();

    /**
     * Default constructor that uses {@link SimnpleFieldNodeFilter} as the Field node filter
     */
    public SimpleFieldCollector() {
        fieldNodeFilter = new SimnpleFieldNodeFilter();
    }

    /**
     * Constructor that uses a supplied fieldNodeFilter as the Field node filter
     * @param fieldNodeFilter instance of {@link FieldNodeFilter}
     */
    public SimpleFieldCollector(FieldNodeFilter fieldNodeFilter) {
        this.fieldNodeFilter = fieldNodeFilter;
    }

    @Override
    public Map<String, List<Field>> collectFields(FieldCollectorParameters parameters, List<Field> fields) {
        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();

        for (Field field : fields) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(parameters, field.getSelectionSet(), visitedFragments, subFields, false);
        }
        return subFields;
    }

    @Override
    public Map<String, List<Field>> collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet) {
        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();

        this.collectFields(parameters, selectionSet, visitedFragments, subFields, false);
        return subFields;
    }

    @Override
    public void collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet, List<String> visitedFragments, Map<String, List<Field>> fields,
            // following arg not used, deliberately; this class is hard-wired to that arg. being false
                              boolean skipDeferred) {
        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, fields, (Field) selection);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, visitedFragments, fields, (InlineFragment) selection, this);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, visitedFragments, fields, (FragmentSpread) selection, this);
            }
        }
    }

    private void collectField(FieldCollectorParameters parameters, Map<String, List<Field>> fields, Field field) {
        if (!fieldNodeFilter.includeNode(parameters.getVariables(), field.getDirectives())) {
            log.debug("{} skipping field: {}", fieldNodeFilter.getClass().getSimpleName(), getFieldEntryKey(field));
            return;
        }
        String name = getFieldEntryKey(field);
        log.debug("{} adding field: {} {}", fieldNodeFilter.getClass().getSimpleName(), name,
                field.getDirectives());
        fields.putIfAbsent(name, new ArrayList<>());
        fields.get(name).add(field);
    }

    static String getFieldEntryKey(Field field) {
        if (field.getAlias() != null) return field.getAlias();
        else return field.getName();
    }

    private void collectFragmentSpread(FieldCollectorParameters parameters, List<String> visitedFragments, Map<String,
            List<Field>> fields, FragmentSpread fragmentSpread, FieldCollector fieldCollector) {
        if (visitedFragments.contains(fragmentSpread.getName())) {
            return;
        }
        if (!fieldNodeFilter.includeNode(parameters.getVariables(), fragmentSpread.getDirectives())) {
            return;
        }
        visitedFragments.add(fragmentSpread.getName());
        FragmentDefinition fragmentDefinition = parameters.getFragmentsByName().get(fragmentSpread.getName());

        if (!fieldNodeFilter.includeNode(parameters.getVariables(), fragmentSpread.getDirectives())) {
            return;
        }
        if (!doesFragmentConditionMatch(parameters, fragmentDefinition)) {
            return;
        }
        fieldCollector.collectFields(parameters, fragmentDefinition.getSelectionSet(), visitedFragments, fields, false);
    }

    private void collectInlineFragment(FieldCollectorParameters parameters, List<String> visitedFragments, Map<String,
            List<Field>> fields, InlineFragment inlineFragment, FieldCollector fieldCollector) {
        if (!fieldNodeFilter.includeNode(parameters.getVariables(), inlineFragment.getDirectives()) ||
                !doesFragmentConditionMatch(parameters, inlineFragment)) {
            return;
        }
        fieldCollector.collectFields(parameters, inlineFragment.getSelectionSet(), visitedFragments, fields, false);
    }

    static boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, InlineFragment inlineFragment) {
        if (inlineFragment.getTypeCondition() == null) {
            return true;
        }
        GraphQLType conditionType;
        conditionType = getTypeFromAST(parameters.getGraphQLSchema(), inlineFragment.getTypeCondition());
        return checkTypeCondition(parameters, conditionType);
    }

    static boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, FragmentDefinition fragmentDefinition) {
        GraphQLType conditionType;
        conditionType = getTypeFromAST(parameters.getGraphQLSchema(), fragmentDefinition.getTypeCondition());
        return checkTypeCondition(parameters, conditionType);
    }

    static boolean checkTypeCondition(FieldCollectorParameters parameters, GraphQLType conditionType) {
        GraphQLObjectType type = parameters.getObjectType();
        if (conditionType.equals(type)) {
            return true;
        }

        if (conditionType instanceof GraphQLInterfaceType) {
            List<GraphQLObjectType> implementations = parameters.getGraphQLSchema().getImplementations((GraphQLInterfaceType)conditionType);
            return implementations.contains(type);
        } else if (conditionType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) conditionType).getTypes().contains(type);
        }
        return false;
    }

}
