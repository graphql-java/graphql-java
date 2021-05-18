package graphql.normalized;


import graphql.Internal;
import graphql.execution.ConditionalNodes;
import graphql.execution.ValuesResolver;


/**
 * Creates a the direct NormalizedFields children, this means it goes only one level deep!
 * This also means the NormalizedFields returned dont have any children.
 */
@Internal
public class FieldCollectorNormalizedQuery {

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();
    private final ValuesResolver valuesResolver = new ValuesResolver();


}
