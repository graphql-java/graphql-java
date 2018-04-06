package graphql.execution;

import graphql.language.Directive;

import java.util.List;
import java.util.Map;

/**
 * A FieldNodeFilter determined whether to include a Field node of not, based on some set of criteria that involved
 * field directives and, perhaps, variables.
 */
public interface FieldNodeFilter {
    /**
     * Used to determine whether to include a Field node.
     * @param variables of a Field node
     * @param directives of a Field node
     * @return include Field node
     */
    boolean includeNode(Map<String, Object> variables, List<Directive> directives);
}
