package graphql.execution;


import graphql.Internal;
import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.execution.DeferredNodes.deferedNode;

/**
 * A field collector can iterate over field selection sets and build out the sub fields that have been selected,
 * expanding named and inline fragments as it goes.s
 *
 * Fields with '@skip' and/or '@include' directive will be resolved to be included, or no.  If included, ONLY fields with the
 * '@defer' directive will be collected.
 */
@Internal
public class DeferredFieldCollector implements FieldCollector {

    FieldCollector fieldCollector = new SimpleFieldCollector();

    @Override
    public Map<String, List<Field>> collectFields(FieldCollectorParameters parameters, List<Field> fields) {
        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();

        for (Field field : fields) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(parameters, field.getSelectionSet(), visitedFragments, subFields, true);
        }
        return subFields;
    }

    @Override
    public Map<String, List<Field>> collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet) {
        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();

        this.collectFields(parameters, selectionSet, visitedFragments, subFields, true);
        return subFields;
    }

    @Override
    public void collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet, List<String> visitedFragments,
                              Map<String, List<Field>> fields, boolean skipDeferred) {
        // get set of all fields
        fieldCollector.collectFields(parameters, selectionSet, visitedFragments, fields, false);

        List<String> keysToRemove= new ArrayList<>();
        for (String key : fields.keySet()) {
            List<Field> fieldsToRemove = new ArrayList<>();
            for (Field field : fields.get(key)) {
                // trim fields that are not @defer
                if (!deferedNode(field.getDirectives())) {
                    fieldsToRemove.add(field);
                }
            }
            if (fieldsToRemove.size() > 0) {
                fields.get(key).removeAll(fieldsToRemove);
            }

            // delete entire map entry if now empty
            if (fields.get(key).size() == 0) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            fields.remove(key);
        }
    }
}

