package graphql.analysis.values

import graphql.AssertException
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputSchemaElement
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedSchemaElement
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import org.jetbrains.annotations.Nullable
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

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
        Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, InputElements inputElements) {
            bumpCount("scalar")
            return coercedValue
        }

        @Override
        Object visitEnumValue(Object coercedValue, GraphQLEnumType inputType, InputElements inputElements) {
            bumpCount("enum")
            return coercedValue
        }

        @Override
        Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, InputElements inputElements) {
            bumpCount("objectField")
            return coercedValue
        }

        @Override
        Map<String, Object> visitInputObjectValue(Map<String, Object> coercedValue, GraphQLInputObjectType inputObjectType, InputElements inputElements) {
            bumpCount("object")
            return coercedValue
        }

        @Override
        List<Object> visitListValue(List<Object> coercedValue, GraphQLList listInputType, InputElements inputElements) {
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
        visitor.visits == ["scalar": 12, "enum": 4, "list": 5, "object": 4, "objectField": 13]


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
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
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
            Object visitEnumValue(Object coercedValue, GraphQLEnumType inputType, ValueVisitor.InputElements inputElements) {
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
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
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
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
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

    def "can visit arguments and change things"() {
        def sdl = """
            type Query {
                field(arg1 : Input!, arg2 : Input, removeArg : Input) : String
            }
            
            input Input {
                name : String
                age : Int
                input : Input
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [
                arg1     :
                        [name: "Tess", age: 42],
                arg2     :
                        [name: "Tom", age: 24],
                removeArg:
                        [name: "Gone-ski", age: 99],
        ]
        def visitor = new ValueVisitor() {
            @Override
            Object visitArgumentValue(@Nullable Object coercedValue, GraphQLArgument graphQLArgument, ValueVisitor.InputElements inputElements) {
                if (graphQLArgument.name == "arg2") {
                    return [name: "Harry Potter", age: 54]
                }
                if (graphQLArgument.name == "removeArg") {
                    return ABSENCE_SENTINEL
                }
                return coercedValue
            }
        }
        when:
        def actual = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)

        def expected = [
                arg1:
                        [name: "Tess", age: 42],
                arg2:
                        [name: "Harry Potter", age: 54]
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
        def newValues = ValueTraverser.visitPreOrder(argValues['arg2'], fieldDef.getArgument("arg2"), visitor)

        then:
        newValues == [name: "Harry Potter", age: 54]

        // catches non sense states
        when:
        ValueTraverser.visitPreOrder([:], fieldDef.getArgument("removeArg"), visitor)

        then:
        thrown(AssertException.class)
    }

    def "can handle applied directive arguments"() {
        def sdl = """
            directive @d(
                arg1 :  Input
                arg2 :  Input
                removeArg :  Input
            ) on FIELD_DEFINITION
            
            type Query {
                field : String @d(
                arg1:
                        {name: "Tom Riddle", age: 42}
                arg2:
                        {name: "Ron Weasley", age: 42}
                removeArg:
                        {name: "Ron Weasley", age: 42}
                )
            }
            
            input Input {
                name : String
                age : Int
                input : Input
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def appliedDirective = fieldDef.getAppliedDirective("d")
        def visitor = new ValueVisitor() {

            @Override
            Object visitScalarValue(@Nullable Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
                if (coercedValue == "Tom Riddle") {
                    return "Happy Potter"
                }
                return coercedValue
            }

            @Override
            Object visitAppliedDirectiveArgumentValue(@Nullable Object coercedValue, GraphQLAppliedDirectiveArgument graphQLAppliedDirectiveArgument, ValueVisitor.InputElements inputElements) {
                if (graphQLAppliedDirectiveArgument.name == "arg2") {
                    return [name: "Harry Potter", age: 54]
                }
                if (graphQLAppliedDirectiveArgument.name == "removeArg") {
                    return ABSENCE_SENTINEL
                }
                return coercedValue
            }
        }


        def appliedDirectiveArgument = appliedDirective.getArgument("arg1")
        when:
        def actual = ValueTraverser.visitPreOrder(appliedDirectiveArgument.getValue(), appliedDirectiveArgument, visitor)

        then:
        actual == [name: "Happy Potter", age: 42]

        when:
        appliedDirectiveArgument = appliedDirective.getArgument("arg2")
        actual = ValueTraverser.visitPreOrder(appliedDirectiveArgument.getValue(), appliedDirectiveArgument, visitor)

        then:
        actual == [name: "Harry Potter", age: 54]


        // catches non sense states
        when:
        appliedDirectiveArgument = appliedDirective.getArgument("removeArg")
        ValueTraverser.visitPreOrder(appliedDirectiveArgument.getValue(), appliedDirectiveArgument, visitor)

        then:
        thrown(AssertException.class)
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
                                         listField      : ["a", "b", "c"],
                                         objectField    : [listField: ["a", "b", "c"]],
                                         stringField    : "s",
                                         leaveAloneField: "ok"
                                 ]
        ]
        def visitor = new ValueVisitor() {

            @Override
            Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, ValueVisitor.InputElements inputElements) {
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
                                        listField      : null,
                                        objectField    : null,
                                        stringField    : null,
                                        leaveAloneField: "ok",
                                ]
        ]
        then:
        actual == expected
    }

    def "can turn nulls into actual values"() {
        def sdl = """
            type Query {
                field(arg : Input) : String
            }
            
            input Input {
                listField : [String]
                objectField : Input
                stringField : String
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [arg:
                                 [
                                         listField  : null,
                                         objectField: null,
                                         stringField: null,
                                 ]
        ]
        def visitor = new ValueVisitor() {

            @Override
            Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, ValueVisitor.InputElements inputElements) {
                if (inputObjectField.name == "listField") {
                    return ["a", "b", "c"]
                }
                if (inputObjectField.name == "objectField") {
                    return [listField: ["x", "y", "z"], stringField: ["will be overwritten"]]
                }
                if (inputObjectField.name == "stringField") {
                    return "stringValue"
                }
                return coercedValue
            }

        }
        when:
        def actual = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)

        def expected = [arg:
                                [
                                        listField  : ["a", "b", "c"],
                                        objectField: [listField: ["a", "b", "c"], stringField: "stringValue"],
                                        stringField: "stringValue",
                                ]
        ]
        then:
        actual == expected
    }


    def "can use the sentinel to remove elements"() {
        def sdl = """

            type Query {
                field(arg : Input!, arg2 : String) : String
            }
            
            input Input {
                name : String 
                age : Int
                extraInput : ExtraInput
                listInput : [Int]
            }

            input ExtraInput {
                name : String 
                gone : Boolean 
                age : Int
                otherInput : ExtraInput
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [arg :
                                 [name      : "Tess",
                                  age       : 42,
                                  extraInput:
                                          [name      : "Tom",
                                           age       : 33,
                                           gone      : true,
                                           otherInput: [
                                                   name: "Ted",
                                                   age : 42]
                                          ],
                                  listInput : [1, 2, 3, 4, 5, 6, 7, 8]
                                 ],
                         arg2: "Gone-ski"
        ]
        def visitor = new ValueVisitor() {
            @Override
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
                def fieldName = inputElements.getLastInputValueDefinition().name
                if (fieldName == "age") {
                    return ABSENCE_SENTINEL
                }
                if (coercedValue == "Gone-ski") {
                    return ABSENCE_SENTINEL
                }
                if (coercedValue == 4 || coercedValue == 7) {
                    return ABSENCE_SENTINEL
                }
                return coercedValue
            }

            @Override
            Object visitInputObjectFieldValue(Object coercedValue, GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, ValueVisitor.InputElements inputElements) {
                def fieldName = inputElements.getLastInputValueDefinition().name
                if (fieldName == "otherInput") {
                    return ABSENCE_SENTINEL
                }
                if (fieldName == "gone") {
                    return ABSENCE_SENTINEL
                }
                return coercedValue
            }
        }
        when:
        def newValues = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)
        then:
        newValues == [arg:
                              [name      : "Tess",
                               extraInput:
                                       [name: "Tom"],
                               listInput : [1, 2, 3, 5, 6, 8]
                              ]
        ]
    }

    def "can get give access to all elements and unwrapped elements"() {
        def sdl = """

            type Query {
                field(arg : Input! ) : String
            }
            
            input Input {
                name : String 
                age : Int 
                objectField : [[Input!]]!
            }
        """
        def schema = TestUtil.schema(sdl)

        def fieldDef = schema.getObjectType("Query").getFieldDefinition("field")
        def argValues = [arg:
                                 [name       : "Tess",
                                  age        : 42,
                                  objectField: [[
                                                        [
                                                                name       : "Tom",
                                                                age        : 33,
                                                                objectField: [[
                                                                                      [
                                                                                              name: "Ted",
                                                                                              age : 42
                                                                                      ]
                                                                              ]]
                                                        ]
                                                ]]
                                 ]
        ]
        def captureAll = []
        def captureUnwrapped = []
        def last = ""
        def visitor = new ValueVisitor() {
            @Override
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
                if (coercedValue == "Ted") {
                    captureAll = inputElements.inputElements.collect { testStr(it) }
                    captureUnwrapped = inputElements.unwrappedInputElements.collect { testStr(it) }
                    last = inputElements.lastInputValueDefinition.name
                }
                return coercedValue
            }

            String testStr(GraphQLInputSchemaElement inputSchemaElement) {
                if (inputSchemaElement instanceof GraphQLNamedSchemaElement) {
                    return inputSchemaElement.name
                }
                return inputSchemaElement.toString()
            }
        }
        when:
        def newValues = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)
        then:
        newValues == argValues
        last == "name"
        captureAll == [
                "arg",
                "Input!",
                "Input",
                "objectField",
                "[[Input!]]!",
                "[[Input!]]",
                "[Input!]",
                "Input!",
                "Input",
                "objectField",
                "[[Input!]]!",
                "[[Input!]]",
                "[Input!]",
                "Input!",
                "Input",
                "name",
                "String",
        ]
        captureUnwrapped == [
                "arg",
                "Input",
                "objectField",
                "Input",
                "objectField",
                "Input",
                "name",
                "String",
        ]
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
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
                checkDirectives(inputElements)
                return coercedValue
            }


            private void checkDirectives(ValueVisitor.InputElements inputElements) {
                def lastElement = inputElements.getLastInputValueDefinition()
                def directive = lastElement.getAppliedDirective("d")
                if (directive != null) {
                    def elementNames = inputElements.getInputElements().collect(
                            { it ->
                                if (it instanceof GraphQLNamedSchemaElement) {
                                    return it.name
                                } else {
                                    it.toString()
                                }
                            })
                            .join(":")
                    def value = directive.getArgument("name").value
                    capture.add(elementNames + "@" + value)
                }
            }
        }
        when:
        def newValues = ValueTraverser.visitPreOrder(argValues, fieldDef, visitor)
        then:
        newValues == argValues
        capture == [
                "arg:Input!:Input:name:String@nameDirective",
                "arg:Input!:Input:age:Int@ageDirective",

                "arg:Input!:Input:input:Input:name:String@nameDirective",
                "arg:Input!:Input:input:Input:age:Int@ageDirective",

                "arg:Input!:Input:input:Input:input:Input:name:String@nameDirective",
                "arg:Input!:Input:input:Input:input:Input:age:Int@ageDirective"
        ]
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
            Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
                def lastElement = inputElements.getLastInputValueDefinition()
                def directive = lastElement.getAppliedDirective("stripHtml")
                if (directive != null) {
                    def v = String.valueOf(coercedValue)
                    return v.replaceAll(/<!--.*?-->/, '').replaceAll(/<.*?>/, '')
                }
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

    def "an integration test showing how to change values"() {
        def sdl = """
directive @stripHtml on ARGUMENT_DEFINITION | INPUT_FIELD_DEFINITION

type Query {
  searchProfile(contains: String! @stripHtml, limit: Int): Profile!
}

type Mutation {
  signUp(input: SignUpInput!): Profile!
}

input SignUpInput {
  username: String! @stripHtml
  password: String!
  firstName: String!
  lastName: String!
}

type Profile {
  username: String!
  fullName: String!
}
"""
        def schemaDirectiveWiring = new SchemaDirectiveWiring() {
            @Override
            GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> env) {
                GraphQLFieldsContainer fieldsContainer = env.getFieldsContainer()
                GraphQLFieldDefinition fieldDefinition = env.getFieldDefinition()
                if (! fieldsContainer instanceof GraphQLObjectType) {
                    return fieldDefinition
                }
                GraphQLObjectType containingObjectType = env.getFieldsContainer() as GraphQLObjectType

                final DataFetcher<?> originalDF = env.getCodeRegistry().getDataFetcher(containingObjectType, fieldDefinition)
                final DataFetcher<?> newDF = { DataFetchingEnvironment originalEnv ->
                    ValueVisitor visitor = new ValueVisitor() {
                        @Override
                        Object visitScalarValue(Object coercedValue, GraphQLScalarType inputType, ValueVisitor.InputElements inputElements) {
                            def container = inputElements.getLastInputValueDefinition()
                            if (container.hasAppliedDirective("stripHtml")) {
                                return stripHtml(coercedValue)
                            }
                            return coercedValue
                        }

                        private String stripHtml(coercedValue) {
                            return String.valueOf(coercedValue)
                                    .replaceAll(/<!--.*?-->/, '')
                                    .replaceAll(/<.*?>/, '')
                        }
                    }
                    DataFetchingEnvironment newEnv = ValueTraverser.visitPreOrder(originalEnv, visitor)
                    return originalDF.get(newEnv);
                }

                env.getCodeRegistry().dataFetcher(containingObjectType, fieldDefinition, newDF)

                return fieldDefinition
            }
        }

        DataFetcher searchProfileDF = { env ->
            def containsArg = env.getArgument("contains") as String
            return [username: containsArg]

        }
        DataFetcher signUpDF = { DataFetchingEnvironment env ->
            def inputArg = env.getArgument("input") as Map<String, Object>
            def inputUserName = inputArg["username"]
            return [username: inputUserName]
        }
        def runtimeWiring = newRuntimeWiring().directiveWiring(schemaDirectiveWiring)
                .type(newTypeWiring("Query").dataFetcher("searchProfile", searchProfileDF))
                .type(newTypeWiring("Mutation").dataFetcher("signUp", signUpDF))
                .build()
        def schema = TestUtil.schema(sdl, runtimeWiring)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = """
                query q {
                    searchProfile(contains : "<b>someHtml</b>") {
                        username
                    }
                }
                
                mutation m {
                    signUp(input : { 
                                username: "<b>bbakerman</b>"
                                password: "hunter2"
                                firstName: "Brad"
                                lastName: "Baker"
                            }
                    ) {
                        username
                    }
                }
"""

        when:
        def executionInput = ExecutionInput.newExecutionInput(query).operationName("q").build()
        def er = graphQL.execute(executionInput)
        then:
        er.errors.isEmpty()
        er.data == [searchProfile: [username: "someHtml"]]

        // mutation
        when:
        executionInput = ExecutionInput.newExecutionInput(query).operationName("m").build()
        er = graphQL.execute(executionInput)
        then:
        er.errors.isEmpty()
        er.data == [signUp: [username: "bbakerman"]]
    }
}
