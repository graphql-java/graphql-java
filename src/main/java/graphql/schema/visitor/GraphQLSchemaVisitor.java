package graphql.schema.visitor;

import graphql.PublicSpi;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedInputType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLUnionType;
import graphql.util.TraversalControl;

/**
 * This visitor interface offers more "smarts" above {@link GraphQLTypeVisitor} and aims to be easier to use
 * with more type safe helpers.
 * <p>
 * You would use it places that need a {@link GraphQLTypeVisitor} by doing `new GraphQLSchemaVisitor() { ...}.toTypeVisitor()`
 */
@PublicSpi
public interface GraphQLSchemaVisitor {

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLAppliedDirective}
     */
    interface AppliedDirectiveVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLAppliedDirective> {
        GraphQLDirectiveContainer getContainer();
    }

    /**
     * Called when visiting a GraphQLAppliedDirective in the schema
     *
     * @param appliedDirective the schema element being visited
     * @param environment      the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitAppliedDirective(GraphQLAppliedDirective appliedDirective, AppliedDirectiveVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLAppliedDirectiveArgument}
     */
    interface AppliedDirectiveArgumentVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLAppliedDirectiveArgument> {

        GraphQLAppliedDirective getContainer();

        /**
         * @return this elements type that has been unwrapped of {@link graphql.schema.GraphQLNonNull} and {@link graphql.schema.GraphQLList}
         */
        GraphQLNamedInputType getUnwrappedType();
    }

    /**
     * Called when visiting a {@link GraphQLAppliedDirectiveArgument} in the schema
     *
     * @param appliedDirectiveArgument the schema element being visited
     * @param environment              the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument appliedDirectiveArgument, AppliedDirectiveArgumentVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLArgument}
     */
    interface ArgumentVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLArgument> {
        /**
         * @return either a {@link GraphQLFieldDefinition} or a {@link graphql.schema.GraphQLDirective}
         */
        GraphQLNamedSchemaElement getContainer();

        /**
         * @return this elements type that has been unwrapped of {@link graphql.schema.GraphQLNonNull} and {@link graphql.schema.GraphQLList}
         */
        GraphQLNamedInputType getUnwrappedType();
    }

    /**
     * Called when visiting a {@link GraphQLArgument} in the schema
     *
     * @param argument    the schema element being visited
     * @param environment the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitArgument(GraphQLArgument argument, ArgumentVisitorEnvironment environment) {
        return environment.ok();
    }

    interface DirectiveVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLDirective> {
    }

    /**
     * Called when visiting a {@link GraphQLArgument} in the schema
     *
     * @param directive    the schema element being visited
     * @param environment the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitDirective(GraphQLDirective directive, DirectiveVisitorEnvironment environment) {
        return environment.ok();
    }
    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLEnumType}
     */
    interface EnumTypeVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLEnumType> {
    }

    /**
     * Called when visiting a {@link GraphQLEnumType} in the schema
     *
     * @param enumType    the schema element being visited
     * @param environment the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitEnumType(GraphQLEnumType enumType, EnumTypeVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLEnumValueDefinition}
     */
    interface EnumValueDefinitionVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLEnumValueDefinition> {
        GraphQLEnumType getContainer();
    }

    /**
     * Called when visiting a {@link GraphQLEnumValueDefinition} in the schema
     *
     * @param enumValueDefinition the schema element being visited
     * @param environment         the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitEnumValueDefinition(GraphQLEnumValueDefinition enumValueDefinition, EnumValueDefinitionVisitorEnvironment environment) {
        return environment.ok();
    }


    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLFieldDefinition}
     */
    interface FieldDefinitionVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLFieldDefinition> {
        GraphQLFieldsContainer getContainer();

        /**
         * @return this elements type that has been unwrapped of {@link graphql.schema.GraphQLNonNull} and {@link graphql.schema.GraphQLList}
         */
        GraphQLNamedOutputType getUnwrappedType();
    }

    /**
     * Called when visiting a {@link GraphQLFieldDefinition} in the schema
     *
     * @param fieldDefinition the schema element being visited
     * @param environment     the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitFieldDefinition(GraphQLFieldDefinition fieldDefinition, FieldDefinitionVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLInputObjectField}
     */
    interface InputObjectFieldVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLInputObjectField> {
        GraphQLInputObjectType getContainer();

        /**
         * @return this elements type that has been unwrapped of {@link graphql.schema.GraphQLNonNull} and {@link graphql.schema.GraphQLList}
         */
        GraphQLNamedInputType getUnwrappedType();
    }

    /**
     * Called when visiting a {@link GraphQLInputObjectField} in the schema
     *
     * @param inputObjectField the schema element being visited
     * @param environment      the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitInputObjectField(GraphQLInputObjectField inputObjectField, InputObjectFieldVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLInputObjectType}
     */
    interface InputObjectTypeVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLInputObjectType> {
    }

    /**
     * Called when visiting a {@link GraphQLInputObjectType} in the schema
     *
     * @param inputObjectType the schema element being visited
     * @param environment     the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitInputObjectType(GraphQLInputObjectType inputObjectType, InputObjectTypeVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLInterfaceType}
     */
    interface InterfaceTypeVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLInterfaceType> {
    }

    /**
     * Called when visiting a {@link GraphQLInterfaceType} in the schema
     *
     * @param interfaceType the schema element being visited
     * @param environment   the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitInterfaceType(GraphQLInterfaceType interfaceType, InterfaceTypeVisitorEnvironment environment) {
        return environment.ok();
    }


    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLObjectType}
     */
    interface ObjectVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLObjectType> {
    }

    /**
     * Called when visiting a {@link GraphQLObjectType} in the schema
     *
     * @param objectType  the schema element being visited
     * @param environment the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitObjectType(GraphQLObjectType objectType, ObjectVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLScalarType}
     */
    interface ScalarTypeVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLScalarType> {
    }

    /**
     * Called when visiting a {@link GraphQLScalarType} in the schema
     *
     * @param scalarType  the schema element being visited
     * @param environment the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitScalarType(GraphQLScalarType scalarType, ScalarTypeVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLUnionType}
     */
    interface UnionTypeVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLUnionType> {
    }

    /**
     * Called when visiting a {@link GraphQLUnionType} in the schema
     *
     * @param unionType   the schema element being visited
     * @param environment the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitUnionType(GraphQLUnionType unionType, UnionTypeVisitorEnvironment environment) {
        return environment.ok();
    }


    /**
     * A {@link GraphQLSchemaVisitorEnvironment} environment specific to {@link GraphQLSchemaElement}
     */
    interface SchemaElementVisitorEnvironment extends GraphQLSchemaVisitorEnvironment<GraphQLSchemaElement> {
    }

    /**
     * Called when visiting any {@link GraphQLSchemaElement} in the schema.  Since every element in the schema
     * is a schema element, this visitor method will be called back for every element in the schema
     *
     * @param schemaElement the schema element being visited
     * @param environment   the visiting environment
     *
     * @return a control value which is typically {@link GraphQLSchemaVisitorEnvironment#ok()}}
     */
    default GraphQLSchemaTraversalControl visitSchemaElement(GraphQLSchemaElement schemaElement, SchemaElementVisitorEnvironment environment) {
        return environment.ok();
    }

    /**
     * This allows you to turn this smarter visitor into the base {@link graphql.schema.GraphQLTypeVisitor} interface
     *
     * @return a type visitor
     */
    default GraphQLTypeVisitor toTypeVisitor() {
        return new GraphQLSchemaVisitorAdapter(this);
    }


}
