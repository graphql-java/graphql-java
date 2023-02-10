package graphql.schema.visitor;

import graphql.PublicSpi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeVisitor;
import graphql.util.TraversalControl;

/**
 * This visitor interface offers more "smarts" above {@link GraphQLTypeVisitor} and aims to be easier to use
 * with more type safe helpers.
 * <p>
 * You would use it places that need a {@link GraphQLTypeVisitor} by doing `new GraphQLSchemaVisitor() { ...}.toTypeVisitor()`
 */
@PublicSpi
public interface GraphQLSchemaVisitor {

    interface ObjectVisitorEnvironment extends GraphQLSchemaVisitorEnvironment {
    }

    default TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, ObjectVisitorEnvironment environment) {
        return TraversalControl.CONTINUE;
    }

    /**
     * This is a class specific for visiting {@link GraphQLFieldDefinition}s
     */
    interface FieldVisitorEnvironment extends GraphQLSchemaVisitorEnvironment {

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
    default TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, FieldVisitorEnvironment environment) {
        return TraversalControl.CONTINUE;
    }


    /**
     * This allows you to turn this smarter visitr into the base {@link graphql.schema.GraphQLTypeVisitor}
     *
     * @return a type visitor
     */
    default GraphQLTypeVisitor toTypeVisitor() {
        return new GraphQLSchemaVisitorAdapter(this);
    }
}
