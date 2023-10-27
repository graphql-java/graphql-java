package graphql.schema.visitor

import graphql.Assert
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
import graphql.schema.GraphQLModifiedType
import graphql.schema.GraphQLNamedSchemaElement
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import graphql.schema.SchemaTransformer
import graphql.schema.SchemaTraverser
import spock.lang.Specification

import static graphql.schema.FieldCoordinates.coordinates

class GraphQLSchemaVisitorTest extends Specification {


    def toNames(GraphQLSchemaElement start, List<GraphQLSchemaElement> elements) {
        def l = elements.collect({
            return GraphQLTypeUtil.simplePrint(it)
        })
        l.add(0, GraphQLTypeUtil.simplePrint((start)))
        return l
    }

    class CapturingSchemaVisitor implements GraphQLSchemaVisitor {

        Map<GraphQLSchemaElement, List<GraphQLSchemaElement>> pathsToElement = [:]
        def types = [:]
        def leafs = [:]
        def schema

        @Override
        GraphQLSchemaTraversalControl visitSchemaElement(GraphQLSchemaElement schemaElement, SchemaElementVisitorEnvironment environment) {
            this.schema = environment.getSchema()
            def leadingElements = environment.getLeadingElements()
            pathsToElement.put(schemaElement, leadingElements)
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitFieldDefinition(GraphQLFieldDefinition fieldDefinition, FieldDefinitionVisitorEnvironment environment) {
            def key = environment.container.getName() + "." + fieldDefinition.getName() + ":" + environment.getUnwrappedType().getName()
            leafs[key] = fieldDefinition
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitAppliedDirective(GraphQLAppliedDirective appliedDirective, AppliedDirectiveVisitorEnvironment environment) {
            def key = "@" + environment.container.getName() + "." + appliedDirective.getName()
            leafs[key] = appliedDirective
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument appliedDirectiveArgument, AppliedDirectiveArgumentVisitorEnvironment environment) {
            def key = "@" + environment.container.getName() + "." + appliedDirectiveArgument.getName() + ":" + environment.getUnwrappedType().getName()
            leafs[key] = appliedDirectiveArgument
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitArgument(GraphQLArgument argument, ArgumentVisitorEnvironment environment) {
            def key = environment.container.getName() + "." + argument.getName() + ":" + environment.getUnwrappedType().getName()
            leafs[key] = argument
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitDirective(GraphQLDirective directive, DirectiveVisitorEnvironment environment) {
            leafs[directive.getName()] = directive
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitEnumType(GraphQLEnumType enumType, EnumTypeVisitorEnvironment environment) {
            types[enumType.getName()] = enumType
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitEnumValueDefinition(GraphQLEnumValueDefinition enumValueDefinition, EnumValueDefinitionVisitorEnvironment environment) {
            leafs[environment.container.getName() + "." + enumValueDefinition.getName()] = enumValueDefinition
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitInputObjectField(GraphQLInputObjectField inputObjectField, InputObjectFieldVisitorEnvironment environment) {
            def key = environment.container.getName() + "." + inputObjectField.getName() + ":" + environment.getUnwrappedType().getName()
            leafs[key] = inputObjectField
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitInputObjectType(GraphQLInputObjectType inputObjectType, InputObjectTypeVisitorEnvironment environment) {
            types[inputObjectType.getName()] = inputObjectType
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitInterfaceType(GraphQLInterfaceType interfaceType, InterfaceTypeVisitorEnvironment environment) {
            types[interfaceType.getName()] = interfaceType
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitScalarType(GraphQLScalarType scalarType, ScalarTypeVisitorEnvironment environment) {
            types[scalarType.getName()] = scalarType
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitUnionType(GraphQLUnionType unionType, UnionTypeVisitorEnvironment environment) {
            types[unionType.getName()] = unionType
            return environment.ok()
        }

        @Override
        GraphQLSchemaTraversalControl visitObjectType(GraphQLObjectType objectType, ObjectVisitorEnvironment environment) {
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
                fieldAToX : [ObjectTypeX!]! 
            }

            type ObjectTypeB {
                fieldB : String
            }
            
            type ObjectTypeX {
                fieldX(arg : InputObjectTypeA) : ObjectTypeX # self referential
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

        // schema paths


        def fieldX = schema.getFieldDefinition(coordinates("ObjectTypeX", "fieldX"))
        def fieldXArg = fieldX.getArgument("arg")
        toNames(fieldXArg, visitor.pathsToElement[fieldXArg]) == [
                "arg",
                "fieldX", "ObjectTypeX", "ObjectTypeX!", "[ObjectTypeX!]", "[ObjectTypeX!]!",
                "fieldAToX", "ObjectTypeA", "ObjectTypeA!", "[ObjectTypeA!]", "[ObjectTypeA!]!",
                "object", "Query"]

        def argInputType = fieldXArg.getType() as GraphQLInputObjectType
        def inputFieldA = argInputType.getFieldDefinition("fieldA")

        toNames(inputFieldA, visitor.pathsToElement[inputFieldA]) == [
                "fieldA", "InputObjectTypeA", "arg",
                "fieldX", "ObjectTypeX", "ObjectTypeX!", "[ObjectTypeX!]", "[ObjectTypeX!]!",
                "fieldAToX", "ObjectTypeA", "ObjectTypeA!", "[ObjectTypeA!]", "[ObjectTypeA!]!",
                "object", "Query"]

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
            GraphQLSchemaTraversalControl visitObjectType(GraphQLObjectType objectType, GraphQLSchemaVisitor.ObjectVisitorEnvironment environment) {
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

    def "can change things at the schema element level and its does not continue"() {
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
            GraphQLSchemaTraversalControl visitSchemaElement(GraphQLSchemaElement schemaElement, GraphQLSchemaVisitor.SchemaElementVisitorEnvironment environment) {
                if (schemaElement instanceof GraphQLObjectType) {
                    GraphQLObjectType objectType = schemaElement
                    if (objectType.name.startsWith("x")) {
                        def newName = objectType.name.replaceFirst("x", "y").capitalize()
                        def newType = objectType.transform { it.name(newName) }
                        return environment.changeNode(newType)
                    }
                }
                return environment.ok();
            }

            @Override
            GraphQLSchemaTraversalControl visitObjectType(GraphQLObjectType objectType, GraphQLSchemaVisitor.ObjectVisitorEnvironment environment) {
                // this wont be called if we changed it
                if (objectType.name.startsWith("x")) {
                    assert false, "This should not be called for X object types"
                }
                return environment.ok();
            }
        }

        when:
        def newSchema = new SchemaTransformer().transform(schema, schemaVisitor.toTypeVisitor())
        then:
        newSchema.getType("Yfoo") instanceof GraphQLObjectType
        newSchema.getType("Ybar") instanceof GraphQLObjectType
    }

    def "can quit visitation"() {

        def visited = []
        def schemaVisitor = new GraphQLSchemaVisitor() {

            @Override
            GraphQLSchemaTraversalControl visitSchemaElement(GraphQLSchemaElement schemaElement, GraphQLSchemaVisitor.SchemaElementVisitorEnvironment environment) {
                def name = GraphQLTypeUtil.simplePrint(schemaElement)
                if (name.toLowerCase().startsWith("x")) {
                    visited.add(name)
                    if (name.contains("Quit")) {
                        return environment.quit()
                    }
                }
                return environment.ok()
            }
        }
        when: // test quit

        def sdl = """
            type Query {
                xField(xQuit : XInputType) : XObjectType
            }
            
            type XObjectType {
                xObj(xArg : String) : XObjectType2
            }

            type XObjectType2 {
                xObj2 : XObjectType2
            }
            
            input XInputType  {
                xinA : String
            } 
        
        """

        def schema = TestUtil.schema(sdl)
        new SchemaTransformer().transform(schema,schemaVisitor.toTypeVisitor())

        then:
        visited == ["xField", "xQuit",]
    }
}
