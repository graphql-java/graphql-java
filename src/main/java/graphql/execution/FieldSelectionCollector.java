package graphql.execution;

import graphql.Internal;
import graphql.language.Field;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;

import java.util.List;
import java.util.Map;

/**
 * Internal because FieldCollector is internal.
 * <p>
 * Provides details of how Selections are handled; e.g., where filtering happens relative to recursion.
 */
@Internal
public interface FieldSelectionCollector {

    void collectField(FieldCollectorParameters parameters, Map<String, List<Field>> fields, Field field);

    void collectFragmentSpread(FieldCollectorParameters parameters, List<String> visitedFragments,
                               Map<String, List<Field>> fields, FragmentSpread fragmentSpread, FieldCollector fieldCollector);

    void collectInlineFragment(FieldCollectorParameters parameters, List<String> visitedFragments, Map<String,
            List<Field>> fields, InlineFragment inlineFragment, FieldCollector fieldCollector);

}
