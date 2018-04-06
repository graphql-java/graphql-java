package graphql.execution;

import graphql.Internal;
import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.List;
import java.util.Map;

/**
 * A field collector can iterate over field selection sets and build out the sub fields that have been selected,
 * expanding named and inline fragments as it goes.s
 */
@Internal
public interface FieldCollector {
    /**
     * Given a list of fields this will collect the sub-field selections and return it as a map
     *
     * @param parameters the parameters to this method
     * @param fields     the list of fields to collect for
     * @return a map of the sub field selections
     */
    Map<String, List<Field>> collectFields(FieldCollectorParameters parameters, List<Field> fields);

    /**
     * Given a selection set this will collect the sub-field selections and return it as a map
     *
     * @param parameters   the parameters to this method
     * @param selectionSet the selection set to collect on
     * @return a map of the sub field selections
     */
    Map<String, List<Field>> collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet);

    /**
     *
     * @param parameters   the parameters to this method
     * @param selectionSet the selection set to collect on
     * @param visitedFragments list of fragments visited
     * @param fields map of the sub fields selected
     * @param skipDeferred if @defer fields should be skipped
     */
    public void collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet, List<String> visitedFragments,
                              Map<String, List<Field>> fields, boolean skipDeferred);

    }
