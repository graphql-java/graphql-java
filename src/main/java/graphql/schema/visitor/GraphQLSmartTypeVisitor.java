package graphql.schema.visitor;

import graphql.PublicSpi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeVisitor;
import graphql.util.TraversalControl;

/**
 * I called it smart because I want to offer more "smarts" above GraphQLTypeVisitor and its "context" uber object
 * <p>
 * You would use it by doing `new GraphQLSmartTypeVisitor() { ...}.toTypeVisitor()`
 */
@PublicSpi
public interface GraphQLSmartTypeVisitor {

    default TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, SmartTypeVisitorEnvironment environment) {
        return TraversalControl.CONTINUE;
    }

    /**
     * This is a class specific for visiting {@link GraphQLFieldDefinition}s
     */
    interface GraphQLFieldDefinitionVisitorEnvironment extends SmartTypeVisitorEnvironment {

        GraphQLFieldsContainer getFieldsContainer();

    }

    /**
     * Each method has a specific env say or some do - TBD
     *
     * @param fieldDefinition the field
     * @param environment     the specific env
     *
     * @return control
     */
    default TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, GraphQLFieldDefinitionVisitorEnvironment environment) {
        return TraversalControl.CONTINUE;
    }


    /**
     * This allows you to turn this smarter visitr into the base {@link graphql.schema.GraphQLTypeVisitor}
     *
     * @return a type visitor
     */
    default GraphQLTypeVisitor toTypeVisitor() {
        return new GraphQLSmartTypeVisitorAdapter(this);
    }
}
