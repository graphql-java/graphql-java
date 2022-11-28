package graphql.analysis.values

import graphql.TestUtil
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.GraphQLDirectiveContainer
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLScalarType
import spock.lang.Specification

class ValueTraverserTest extends Specification {

    def sdl = """
            type Query {
                field(arg1 : ComplexInput, arg2 : ComplexInput, stringArg : String, enumArg : RGB) : String
            } 
            
            input ComplexInput {
                complexListField : [[ComplexInput!]]
                objectField : InnerInput
                listField : [Int!]
                stringField : String
                enumField : RGB
            }

            input InnerInput {
                innerListField : [Int!]
                innerStringField : String
                innerEnumField : RGB
            }
                
            
            enum RGB {
                RED,GREEN,BLUE
            }
        """

    def schema = TestUtil.schema(sdl)

    class CountingVisitor implements ValueVisitor {

        Map<String, Integer> visits = [:]

        private int bumpCount(String name) {
            visits.compute(name, { k, v -> return v == null ? 0 : ++v })
        }

        @Override
        Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
            bumpCount("scalar")
            return coercedValue
        }

        @Override
        Object visitEnumValue(Object coercedValue, GraphQLEnumType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
            bumpCount("enum")
            return coercedValue
        }

        @Override
        Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, int index, List<GraphQLDirectiveContainer> containingElements) {
            bumpCount("objectField")
            return coercedValue
        }

        @Override
        Map<String, Object> visitInputObjectValue(Map<String, Object> coercedValue, GraphQLInputObjectType inputObjectType, int index, List<GraphQLDirectiveContainer> containingElements) {
            bumpCount("object")
            return coercedValue
        }

        @Override
        List<Object> visitListValue(List<Object> coercedValue, GraphQLList listInputType, int index, List<GraphQLDirectiveContainer> containingElements) {
            bumpCount("list")
            return coercedValue
        }
    }

    def "can traverse and changes nothing at all"() {

        def fieldDef = this.schema.getObjectType("Query").getFieldDefinition("field")

        def innerInput = [
                innerListField  : [6, 7, 8],
                innerStringField: "Inner",
                innerEnumField  : "RED",
        ]
        def complexInput = [
                complexListField: [[[objectField: innerInput, complexListField: [[]], stringField: "There", enumField: "GREEN"]]],
                objectField     : [innerStringField: "World", innerEnumField: "BLUE"],
                listField       : [1, 2, 3, 4, 5],
                stringField     : "Hello",
                enumField       : "RED",
        ]
        Map<String, Object> originalValues = [
                arg1       : complexInput,
                arg2       : null,
                stringArg  : "Hello",
                enumArg    : "RGB",
                noFieldData: "wat",
        ]
        def visitor = new CountingVisitor()
        when:
        def newValues = ValueTraverser.visitPreOrder(originalValues, fieldDef, visitor)
        then:
        // nothing changed - its the same object
        newValues === originalValues
        visitor.visits == ["scalar": 12, "enum": 4, "list": 5, "object": 3, "objectField": 13]


        when:
        def originalDFE = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().fieldDefinition(fieldDef).graphQLSchema(this.schema).arguments(originalValues).build()
        def newDFE = ValueTraverser.visitPreOrder(originalDFE, visitor)

        then:
        newDFE === originalDFE

        when:
        def graphQLArgument = fieldDef.getArgument("arg1")
        def newValue = ValueTraverser.visitPreOrder(complexInput, graphQLArgument, visitor)

        then:
        complexInput === newValue
    }

    def "can change simple values"() {
        def fieldDef = this.schema.getObjectType("Query").getFieldDefinition("field")

        def innerInput = [
                innerListField  : [6, 7, 8],
                innerStringField: "Inner",
                innerEnumField  : "RED",
        ]
        def complexInput = [
                complexListField: [[[objectField: innerInput, complexListField: [[]], stringField: "There", enumField: "GREEN"]]],
                objectField     : [innerStringField: "World", innerEnumField: "BLUE"],
                listField       : [1, 2, 3, 4, 5],
                stringField     : "Hello",
                enumField       : "RED",
        ]
        Map<String, Object> originalValues = [
                arg1       : complexInput,
                arg2       : null,
                stringArg  : "Hello",
                enumArg    : "BLUE",
                noFieldData: "wat",
        ]
        def visitor = new ValueVisitor() {
            @Override
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
                if (coercedValue instanceof String) {
                    def val = coercedValue as String
                    return val.toUpperCase().reverse()
                }
                if (coercedValue instanceof Number) {
                    return coercedValue * 1000
                }
                return coercedValue;
            }

            @Override
            Object visitEnumValue(Object coercedValue, GraphQLEnumType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
                def val = coercedValue as String
                return val.toLowerCase().reverse()
            }
        }
        when:
        def newValues = ValueTraverser.visitPreOrder(originalValues, fieldDef, visitor)
        then:
        // numbers are 1000 greater and strings are upper case reversed and enums are lower cased reversed
        newValues == [
                arg1       : [complexListField: [[[
                                                          objectField     : [
                                                                  innerListField  : [6000, 7000, 8000],
                                                                  innerStringField: "RENNI",
                                                                  innerEnumField  : "der"
                                                          ],
                                                          complexListField: [[]],
                                                          stringField     : "EREHT",
                                                          enumField       : "neerg"]]],
                              objectField     : [innerStringField: "DLROW", innerEnumField: "eulb"],
                              listField       : [1000, 2000, 3000, 4000, 5000],
                              stringField     : "OLLEH",
                              enumField       : "der"],
                arg2       : null,
                stringArg  : "OLLEH",
                enumArg    : "eulb",
                noFieldData: "wat"
        ]
    }

    def "can change a list midway through "() {
        def sdl = """
            type Query {
                field(arg : [Int]!) : String
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [arg: [1, 2, 3, 4]]
        def visitor = new ValueVisitor() {
            @Override
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
                if (coercedValue == 3) {
                    return 33
                }
                return coercedValue
            }
        }
        when:
        def newValues = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)
        then:
        newValues == [arg: [1, 2, 33, 4]]
    }

    def "can change an object midway through "() {
        def sdl = """
            type Query {
                field(arg : Input!) : String
            }
            
            input Input {
                name : String
                age : Int
                input : Input
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [arg:
                                 [name: "Tess", age: 42, input:
                                         [name: "Tom", age: 33, input:
                                                 [name: "Ted", age: 42]]
                                 ]
        ]
        def visitor = new ValueVisitor() {
            @Override
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
                if (coercedValue == 42) {
                    return 24
                }
                return coercedValue
            }
        }
        when:
        def actual = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)

        def expected = [arg:
                                [name: "Tess", age: 24, input:
                                        [name: "Tom", age: 33, input:
                                                [name: "Ted", age: 24]
                                        ]
                                ]
        ]
        then:
        actual == expected


        // can change a DFE arguments
        when:
        def startingDFE = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().fieldDefinition(fieldDef).arguments(argValues).build()
        def newDFE = ValueTraverser.visitPreOrder(startingDFE, visitor)

        then:
        newDFE.getArguments() == expected
        newDFE.getFieldDefinition() == fieldDef

        // can change a single arguments
        when:
        def newValues = ValueTraverser.visitPreOrder(argValues['arg'], fieldDef.getArgument("arg"), visitor)

        then:
        newValues == [name: "Tess", age: 24, input:
                [name: "Tom", age: 33, input:
                        [name: "Ted", age: 24]
                ]
        ]
    }

    def "can handle a null changes"() {
        def sdl = """
            type Query {
                field(arg : Input!) : String
            }
            
            input Input {
                listField : [String!]
                objectField : Input
                stringField : String
                leaveAloneField : String
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [arg:
                                 [
                                         listField  : ["a", "b", "c"],
                                         objectField: [listField: ["a", "b", "c"]],
                                         stringField: "s",
                                         leaveAloneField: "ok"
                                 ]
        ]
        def visitor = new ValueVisitor() {

            @Override
            Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, int index, List<GraphQLDirectiveContainer> containingElements) {
                if (inputObjectField.name == "leaveAloneField") {
                    return coercedValue
                }
                return null
            }

        }
        when:
        def actual = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)

        def expected = [arg:
                                [
                                        listField  : null,
                                        objectField: null,
                                        stringField: null,
                                        leaveAloneField: "ok",
                                ]
        ]
        then:
        actual == expected
    }

    def "can get access to directives"() {
        def sdl = """
            directive @d(name : String!) on ARGUMENT_DEFINITION | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                field(arg : Input! @d(name :  "argDirective") ) : String
            }
            
            input Input {
                name : String @d(name : "nameDirective")
                age : Int @d(name : "ageDirective")
                input : Input @d(name : "inputDirective")
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [arg:
                                 [name: "Tess", age: 42, input:
                                         [name: "Tom", age: 33, input:
                                                 [name: "Ted", age: 42]]
                                 ]
        ]
        def capture = []
        def visitor = new ValueVisitor() {
            @Override
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
                checkDirectives(containingElements)
                return coercedValue
            }

            @Override
            Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, int index, List<GraphQLDirectiveContainer> containingElements) {
                checkDirectives(containingElements)
                return coercedValue
            }

            private void checkDirectives(List<GraphQLDirectiveContainer> containingElements) {
                def lastElement = containingElements.last()
                def directive = lastElement.getAppliedDirective("d")
                if (directive != null) {
                    def elementNames = containingElements.collect({ it -> it.name }).join(":")
                    def value = directive.getArgument("name").value
                    capture.add(elementNames + "@" + value)
                }
            }
        }
        when:
        def newValues = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)
        then:
        newValues == argValues
        capture == ["field:arg:name@nameDirective",
                    "field:arg:age@ageDirective",
                    "field:arg:input:name@nameDirective",

                    "field:arg:input:age@ageDirective",
                    "field:arg:input:input:name@nameDirective",
                    "field:arg:input:input:age@ageDirective"]
    }

    def "can follow directives and change input"() {
        def sdl = """
            directive @stripHtml on ARGUMENT_DEFINITION | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                field(arg : Input!) : String
            }
            
            input Input {
                name : String @stripHtml
                age : Int
                input : Input
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [arg:
                                 [name: "<b>Tess</b>", age: 42, input:
                                         [name: "<u>Tom</u>", age: 33, input:
                                                 [name: "<i>Ted</i>", age: 42]]
                                 ]
        ]

        def visitor = new ValueVisitor() {
            @Override
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, int index, List<GraphQLDirectiveContainer> containingElements) {
                def lastElement = containingElements.last()
                def directive = lastElement.getAppliedDirective("stripHtml")
                if (directive != null) {
                    def v = String.valueOf(coercedValue)
                    return v.replaceAll(/<!--.*?-->/, '').replaceAll(/<.*?>/, '')
                }
                return coercedValue
            }

            @Override
            Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, int index, List<GraphQLDirectiveContainer> containingElements) {
                return coercedValue
            }

        }
        when:
        def newValues = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)
        then:
        newValues == [arg:
                              [name: "Tess", age: 42, input:
                                      [name: "Tom", age: 33, input:
                                              [name: "Ted", age: 42]]
                              ]
        ]
    }
}
