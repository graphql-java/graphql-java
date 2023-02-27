package graphql.schema.visitor

import graphql.TestUtil
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLUnionType
import graphql.schema.SchemaTransformer
import graphql.schema.SchemaTraverser
import graphql.util.TraversalControl
import spock.lang.Specification

class GraphQLSchemaVisitorTest extends Specification {


    class CapturingSchemaVisitor implements GraphQLSchemaVisitor {

        def types = [:]
        def leafs = [:]
        def schema

        @Override
        TraversalControl visitSchemaElement(GraphQLSchemaElement schemaElement, SchemaElementVisitorEnvironment environment) {
            this.schema = environment.getSchema()
            return environment.ok()
        }

        @Override
        TraversalControl visitFieldDefinition(GraphQLFieldDefinition fieldDefinition, FieldDefinitionVisitorEnvironment environment) {
            def key = environment.container.getName() + "." + fieldDefinition.getName() + ":" + environment.getUnwrappedType().getName()
            leafs[key] = fieldDefinition
            return environment.ok()
        }

        @Override
        TraversalControl visitAppliedDirective(GraphQLAppliedDirective appliedDirective, AppliedDirectiveVisitorEnvironment environment) {
            def key = "@" + environment.container.getName() + "." + appliedDirective.getName()
            leafs[key] = appliedDirective
            return environment.ok()
        }

        @Override
        TraversalControl visitAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument appliedDirectiveArgument, AppliedDirectiveArgumentVisitorEnvironment environment) {
            def key = "@" + environment.container.getName() + "." + appliedDirectiveArgument.getName() + ":" + environment.getUnwrappedType().getName()
            leafs[key] = appliedDirectiveArgument
            return environment.ok()
        }

        @Override
        TraversalControl visitArgument(GraphQLArgument argument, ArgumentVisitorEnvironment environment) {
            def key = environment.container.getName() + "." + argument.getName() + ":" + environment.getUnwrappedType().getName()
            leafs[key] = argument
            return environment.ok()
        }

        @Override
        TraversalControl visitDirective(GraphQLDirective directive, DirectiveVisitorEnvironment environment) {
            leafs[directive.getName()] = directive
            return environment.ok()
        }

        @Override
        TraversalControl visitEnumType(GraphQLEnumType enumType, EnumTypeVisitorEnvironment environment) {
            types[enumType.getName()] = enumType
            return environment.ok()
        }

        @Override
        TraversalControl visitEnumValueDefinition(GraphQLEnumValueDefinition enumValueDefinition, EnumValueDefinitionVisitorEnvironment environment) {
            leafs[environment.container.getName() + "." + enumValueDefinition.getName()] = enumValueDefinition
            return environment.ok()
        }

        @Override
        TraversalControl visitInputObjectField(GraphQLInputObjectField inputObjectField, InputObjectFieldVisitorEnvironment environment) {
            def key = environment.container.getName() + "." + inputObjectField.getName() + ":" + environment.getUnwrappedType().getName()
            leafs[key] = inputObjectField
            return environment.ok()
        }

        @Override
        TraversalControl visitInputObjectType(GraphQLInputObjectType inputObjectType, InputObjectTypeVisitorEnvironment environment) {
            types[inputObjectType.getName()] = inputObjectType
            return environment.ok()
        }

        @Override
        TraversalControl visitInterfaceType(GraphQLInterfaceType interfaceType, InterfaceTypeVisitorEnvironment environment) {
            types[interfaceType.getName()] = interfaceType
            return environment.ok()
        }

        @Override
        TraversalControl visitScalarType(GraphQLScalarType scalarType, ScalarTypeVisitorEnvironment environment) {
            types[scalarType.getName()] = scalarType
            return environment.ok()
        }

        @Override
        TraversalControl visitUnionType(GraphQLUnionType unionType, UnionTypeVisitorEnvironment environment) {
            types[unionType.getName()] = unionType
            return environment.ok()
        }

        @Override
        TraversalControl visitObjectType(GraphQLObjectType objectType, ObjectVisitorEnvironment environment) {
            types[objectType.getName()] = objectType
            return environment.ok()
        }
    }

    def uberSDL = '''
            directive @directive(directiveArgument : String) on FIELD_DEFINITION
            
            type Query {
                object(arg : InputObjectTypeA) : [ObjectTypeA!]! @directive(directiveArgument : "directiveArgument")
            }
            
            input InputObjectTypeA {
                fieldA : String!
            }

            input InputObjectTypeB {
                fieldB : String
            }
            
            interface InterfaceTypeA {
                fieldA : [String!]!
            }
            
            type ObjectTypeA implements InterfaceTypeA {
                fieldA : [String!]!
            }

            type ObjectTypeB {
                fieldB : String
            }
            
            union UnionTypeA = ObjectTypeA | ObjectTypeB
            
            enum EnumTypeA {
                enumDefA
                enumDefB
            }
    '''

    def schema = TestUtil.schema(uberSDL)


    def "will visit things"() {

        def visitor = new CapturingSchemaVisitor()

        when:
        new SchemaTraverser().depthFirstFullSchema(visitor.toTypeVisitor(), this.schema)

        then:

        visitor.schema == this.schema
        visitor.types["Query"] instanceof GraphQLObjectType

        visitor.leafs["directive"] instanceof GraphQLDirective
        visitor.leafs["directive.directiveArgument:String"] instanceof GraphQLArgument

        visitor.leafs["@object.directive"] instanceof GraphQLAppliedDirective
        visitor.leafs["@directive.directiveArgument:String"] instanceof GraphQLAppliedDirectiveArgument

        visitor.types["EnumTypeA"] instanceof GraphQLEnumType
        visitor.leafs["EnumTypeA.enumDefA"] instanceof GraphQLEnumValueDefinition
        visitor.leafs["EnumTypeA.enumDefB"] instanceof GraphQLEnumValueDefinition

        visitor.types["InputObjectTypeA"] instanceof GraphQLInputObjectType
        visitor.types["InputObjectTypeB"] instanceof GraphQLInputObjectType
        visitor.leafs["InputObjectTypeA.fieldA:String"] instanceof GraphQLInputObjectField

        visitor.types["InterfaceTypeA"] instanceof GraphQLInterfaceType
        visitor.leafs["InterfaceTypeA.fieldA:String"] instanceof GraphQLFieldDefinition

        visitor.types["ObjectTypeA"] instanceof GraphQLObjectType
        visitor.types["ObjectTypeB"] instanceof GraphQLObjectType
        visitor.leafs["ObjectTypeA.fieldA:String"] instanceof GraphQLFieldDefinition

        visitor.types["String"] instanceof GraphQLScalarType

        visitor.types["UnionTypeA"] instanceof GraphQLUnionType

    }

    def "can transform schemas via this pattern"() {
        def sdl = """
               type Query {
                    f : xfoo
               }
               
               type xfoo {
                    bar : xbar
               }
                
               type xbar {
                 baz : String
               } 
                    
        """

        def schema = TestUtil.schema(sdl)

        def schemaVisitor = new GraphQLSchemaVisitor() {

            @Override
            TraversalControl visitObjectType(GraphQLObjectType objectType, GraphQLSchemaVisitor.ObjectVisitorEnvironment environment) {
                if (objectType.name.startsWith("x")) {
                    def newName = objectType.name.replaceFirst("x", "").capitalize()
                    def newType = objectType.transform { it.name(newName) }
                    return environment.changeNode(newType)
                }
                return environment.ok();
            }
        }

        when:
        def newSchema = new SchemaTransformer().transform(schema, schemaVisitor.toTypeVisitor())
        then:
        newSchema.getType("Foo") instanceof GraphQLObjectType
        newSchema.getType("Bar") instanceof GraphQLObjectType
    }
}
