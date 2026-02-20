package graphql.util

import graphql.AssertException
import graphql.TestUtil
import graphql.Directives
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

class AnonymizerTest extends Specification {

    def "simple schema and query"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            bar1: String
            bar2: ID
        }
        """)
        def query = "{foo{bar1 bar2}}"

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1: Object2
}

type Object2 {
  field2: String
  field3: ID
}
"""
        newQuery == "{field1{field2 field3}}"
    }

    def "query with fragments"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            bar1: String
            bar2: ID
        }
        """)
        def query = "{...MyFragment foo {... on Foo{bar1 bar2}}} fragment MyFragment on Query{foo {bar1 bar2 }}"

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1: Object2
}

type Object2 {
  field2: String
  field3: ID
}
"""
        newQuery == "{...Fragment1 field1{...on Object2{field2 field3}}} fragment Fragment1 on Object1 {field1{field2 field3}}"

    }

    def "query with arguments"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo(id: ID, otherArg: String): Foo
        }
        type Foo {
            bar1(someArg: Boolean): String
            bar2: ID
        }
        """)
        def query = '{foo(id: "123", otherArg: "456") {bar1 bar2 }}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1(argument1: ID, argument2: String): Object2
}

type Object2 {
  field2(argument3: Boolean): String
  field3: ID
}
"""
        newQuery == '{field1(argument1:"stringValue1",argument2:"stringValue2"){field2 field3}}'

    }

    def "query with operation and variable names"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo(id: ID, otherArg: String): Foo
        }
        type Foo {
            bar1(someArg: Boolean): String
            bar2(otherId: ID): ID
        }
        """)
        def query = 'query myOperation($myVar:ID) {foo(id: $myVar, otherArg: "456") {bar1 bar2(otherId: $myVar) }}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query], [myVar: "myVarValue"])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1(argument1: ID, argument2: String): Object2
}

type Object2 {
  field2(argument3: Boolean): String
  field3(argument4: ID): ID
}
"""
        newQuery == 'query operation($var1:ID){field1(argument1:$var1,argument2:"stringValue1"){field2 field3(argument4:$var1)}}'


    }

    def "rejects query with multiple operations"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo: String
        }
        """)
        def query = 'query myOperation{foo} query myOtherQuery{foo}'

        when:
        Anonymizer.anonymizeSchemaAndQueries(schema, [query])

        then:
        def assertException = thrown(AssertException)
        assertException.getMessage().contains("Query must have exactly one operation")

    }

    def "replace values"() {
        given:
        def schema = TestUtil.schema("""

        type Query {
            foo(myInput: MyInput!): String
            foo2(arg: String = "toBeReplaced"): String
            foo3(arg: [[String!]!]! = [["defaultValueList"]]): String
            foo4(arg: Weekend! = SATURDAY): String
            foo5(arg: MyInput! = { foo2: "default", foo5: SUNDAY, foo6: { foo1: 10 } } ): String
            foo6: Object
        }
        input MyInput {
            foo1: Int
            foo2: String = "myDefaultValue"
            foo3: Int = 1234
            foo4: Int = 4567 
            foo5: Weekend = SUNDAY
            foo6: MyInput
        }
        
        enum Weekend {
            SATURDAY
            SUNDAY
        }
        
        type Object implements Iface {
            id: String
            # default value must match across hierarchy
            bar(arg: MyInput = {foo2: "adefault", foo5: SATURDAY}): String
        }
        
        interface Iface {
            id: String
           
            bar(arg: MyInput = {foo2: "adefault", foo5: SATURDAY}): String
        }
        """)
        def query = '''
        query myQuery($myVar: String = "someValue", 
                        $varFoo3: [[String!]!]! = [["defaultValueList"]],
                        $varFoo4: Weekend! = SATURDAY,
                        $varFoo5: MyInput! = { foo2: "default", foo5: SUNDAY, foo6: { foo1: 10 } }){
            foo(myInput: {foo1: 8923, foo2: $myVar })
            foo3(arg: $varFoo3)
            foo4(arg: $varFoo4)
            foo5(arg: $varFoo5)
        }
        '''

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """\
        schema {
          query: Object1
        }
        
        interface Interface1 {
          field7: String
          field8(argument6: InputObject1 = {inputField2 : "stringValue5", inputField5 : EnumValue1}): String
        }
        
        type Object1 {
          field1(argument1: InputObject1!): String
          field2(argument2: String = "stringValue2"): String
          field3(argument3: [[String!]!]! = [["stringValue3"]]): String
          field4(argument4: Enum1! = EnumValue1): String
          field5(argument5: InputObject1! = {inputField2 : "stringValue4", inputField5 : EnumValue2, inputField6 : {inputField1 : 3}}): String
          field6: Object2
        }
        
        type Object2 implements Interface1 {
          field7: String
          field8(argument6: InputObject1 = {inputField2 : "stringValue5", inputField5 : EnumValue1}): String
        }
        
        enum Enum1 {
          EnumValue1
          EnumValue2
        }
        
        input InputObject1 {
          inputField1: Int
          inputField2: String = "stringValue1"
          inputField3: Int = 1
          inputField4: Int = 2
          inputField5: Enum1 = EnumValue2
          inputField6: InputObject1
        }
        """.stripIndent()
        newQuery == 'query operation($var1:String="stringValue1",$var2:[[String!]!]!=[["stringValue2"]],$var3:Enum1!=EnumValue1,$var4:InputObject1!={inputField2:"stringValue3",inputField5:EnumValue2,inputField6:{inputField1:2}}){field1(argument1:{inputField1:1,inputField2:$var1}) field3(argument3:$var2) field4(argument4:$var3) field5(argument5:$var4)}'
    }

    def "query with aliases"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            bar1: String
            bar2: ID
        }
        """)
        def query = "{myAlias: foo { anotherOne: bar2}}"

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newQuery = result.queries[0]

        then:
        newQuery == "{alias1:field1{alias2:field3}}"
    }

    def "complex schema"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            pets: Pet
            allPets: AllPets
        }
        enum PetKind {
            FRIENDLY
            NOT_FRIENDLY
        }
        
        interface Pet {
            name: String
            petKind: PetKind
        }
        type Dog implements Pet {
            name: String 
            dogField: String
            petKind: PetKind
        } 
        type Cat implements Pet {
            name: String 
            catField: String
            petKind: PetKind
        }
        union AllPets = Dog | Cat
        """)

        when:
        def result = Anonymizer.anonymizeSchema(schema)
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result)

        then:
        newSchema == """schema {
  query: Object1
}

interface Interface1 {
  field2: String
  field3: Enum1
}

union Union1 = Object2 | Object3

type Object1 {
  field1: Interface1
  field4: Union1
}

type Object2 implements Interface1 {
  field2: String
  field3: Enum1
  field5: String
}

type Object3 implements Interface1 {
  field2: String
  field3: Enum1
  field6: String
}

enum Enum1 {
  EnumValue1
  EnumValue2
}
"""
    }

    def "interface hierarchies with arguments"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            pets: Pet
        }
        
        interface Pet {
            name: String
        }
        interface GoodPet implements Pet {
            name(nameArg1: String): String 
            goodScore: Int
        } 
        type Cat implements GoodPet & Pet{
            name(nameArg1:String, nameArg2: ID): String 
            goodScore: Int
            catField: ID
        }
        
        interface ProblematicPet implements Pet {
            name(nameArg3:String): String 
            problemField: Float
       } 
        interface AnotherInterface implements ProblematicPet & Pet {
            name(nameArg3: String, nameArg4: Float): String 
            problemField: Float
            otherField: Boolean 
        }
        type Dog implements AnotherInterface & ProblematicPet & Pet {
            name(nameArg3: String, nameArg4: Float): String 
            problemField: Float
            otherField: Boolean 
            dogField: Int
        }
        """)

        when:
        def result = Anonymizer.anonymizeSchema(schema)
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result)

        then:
        newSchema == """schema {
  query: Object3
}

interface Interface1 implements Interface2 {
  field1(argument1: String): String
  field2: Int
}

interface Interface2 {
  field1: String
}

interface Interface3 implements Interface2 {
  field1(argument3: String): String
  field4: Float
}

interface Interface4 implements Interface2 & Interface3 {
  field1(argument3: String, argument4: Float): String
  field4: Float
  field5: Boolean
}

type Object1 implements Interface1 & Interface2 {
  field1(argument1: String, argument2: ID): String
  field2: Int
  field3: ID
}

type Object2 implements Interface2 & Interface3 & Interface4 {
  field1(argument3: String, argument4: Float): String
  field4: Float
  field5: Boolean
  field6: Int
}

type Object3 {
  field7: Interface2
}
"""
    }

    def "simple interface hierarchies with arguments"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            pets: Pet
        }
        
        interface Pet {
            name(nameArg: String): String
        }
        type Dog implements  Pet {
            name(nameArg: String, otherOptionalArg: Float): String 
        }
        """)

        when:
        def result = Anonymizer.anonymizeSchema(schema)
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result)

        then:
        newSchema == """schema {
  query: Object2
}

interface Interface1 {
  field1(argument1: String): String
}

type Object1 implements Interface1 {
  field1(argument1: String, argument2: Float): String
}

type Object2 {
  field2: Interface1
}
"""
    }

    def "query with introspection typename"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            pets: Pet
        }
        interface Pet {
            name:String
        }
        type Dog implements  Pet {
            name:String
        }
        """)

        def query = "{pets {__typename otherTypeName:__typename name}}"

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newQuery = result.queries[0]

        then:
        newQuery == "{field2{__typename alias1:__typename field1}}"
    }

    def "handles cyclic types"() {
        def schema = TestUtil.schema("""
            type Query {
                query: Foo
            }
            type Foo {
                foo: [Bar]
            }

            type Bar {
                bar: [Foo]
            }
        """)
        when:
        def result = Anonymizer.anonymizeSchema(schema)
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result)

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1: Object2
}

type Object2 {
  field2: [Object3]
}

type Object3 {
  field3: [Object2]
}
"""
    }

    def "descriptions are removed"() {
        def schema = TestUtil.schema("""
            "DOC"
            type Query {
                "DOC"
                query(
                "DOC"
                arg: String): Foo
            }
            "DOC"
            type Foo {
                "DOC"
                foo: String
            }

        """)
        when:
        def result = Anonymizer.anonymizeSchema(schema)
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result)

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1(argument1: String): Object2
}

type Object2 {
  field2: String
}
"""
    }

    def "deprecated reasons are removed"() {
        def schema = TestUtil.schema("""
            type Query {
                foo: String @deprecated(reason: "secret")
            }
        """)
        when:
        def result = Anonymizer.anonymizeSchema(schema)
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result)

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1: String @deprecated
}
"""
    }

    def "same field across hierarchy"() {
        def schema = TestUtil.schema("""
            type Query {
                foo: Interface2
            }
interface Interface1 implements Interface2 & Interface3 {
  id: ID!
}
interface Interface4 implements Interface1 & Interface2 & Interface3 {
  id: ID!
}
interface Interface5 implements Interface1 & Interface2 & Interface3 & Interface6{
  id: ID!
}
interface Interface2 {
  id: ID!
}

interface Interface3 {
  id: ID!
}
interface Interface6 {
  id: ID!
}

interface Interface7 implements Interface6 {
  id: ID!
}


        """)
        when:
        def result = Anonymizer.anonymizeSchema(schema)
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result)

        then:
        newSchema == """schema {
  query: Object1
}

interface Interface1 implements Interface2 & Interface3 {
  field1: ID!
}

interface Interface2 {
  field1: ID!
}

interface Interface3 {
  field1: ID!
}

interface Interface4 implements Interface1 & Interface2 & Interface3 {
  field1: ID!
}

interface Interface5 implements Interface1 & Interface2 & Interface3 & Interface6 {
  field1: ID!
}

interface Interface6 {
  field1: ID!
}

interface Interface7 implements Interface6 {
  field1: ID!
}

type Object1 {
  field2: Interface2
}
"""
    }

    def "complex schema with directives"() {
        given:
        def schema = TestUtil.schema("""
        directive @key(fields: String! = "sensitive") repeatable on SCHEMA | SCALAR 
                            | OBJECT 
                            | FIELD_DEFINITION 
                            | ARGUMENT_DEFINITION 
                            | INTERFACE 
                            | UNION 
                            | ENUM 
                            | ENUM_VALUE 
                            | INPUT_OBJECT 
                            | INPUT_FIELD_DEFINITION 

        schema @key(fields: "hello") {
           query: Query
        }

        type Query @key(fields: "hello2") {
            pets: Pet @key(fields: "hello3")
            allPets: AllPets @deprecated(reason: "no money")
        }
        
        enum PetKind @key(fields: "hello4") {
            FRIENDLY @key(fields: "hello5")
            NOT_FRIENDLY
        }
        
        interface Pet @key(fields: "hello6") {
            name: String
            petKind: PetKind
        }
        type Dog implements Pet {
            name: String 
            dogField(limit: LimitInput): String
            petKind: PetKind
        } 
        
        input LimitInput @key(fields: "hello7") {
            value: Int @key(fields: "hello8")
        }
       
        union AllPets @key(fields: "hello9") = Dog
        """)

        when:
        def result = Anonymizer.anonymizeSchema(schema)
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions()
                .includeDirectives({!Directives.isBuiltInDirective(it) || it == "deprecated"}))
                .print(result)

        then:
        newSchema == """schema @Directive1(argument1 : "stringValue1"){
  query: Object1
}

directive @Directive1(argument1: String! = "stringValue4") repeatable on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String! = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

interface Interface1 @Directive1(argument1 : "stringValue12") {
  field2: String
  field3: Enum1
}

union Union1 @Directive1(argument1 : "stringValue21") = Object2

type Object1 @Directive1(argument1 : "stringValue8") {
  field1: Interface1 @Directive1(argument1 : "stringValue9")
  field4: Union1 @deprecated
}

type Object2 implements Interface1 {
  field2: String
  field3: Enum1
  field5(argument2: InputObject1): String
}

enum Enum1 @Directive1(argument1 : "stringValue15") {
  EnumValue1 @Directive1(argument1 : "stringValue18")
  EnumValue2
}

input InputObject1 @Directive1(argument1 : "stringValue24") {
  inputField1: Int @Directive1(argument1 : "stringValue27")
}
"""
    }

    def "query with directives"() {
        given:
        def schema = TestUtil.schema("""
        directive @whatever(myArg: String = "secret") on FIELD 
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        """)
        def query = 'query{foo @whatever {bar @whatever }}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(SchemaPrinter.ExcludeGraphQLSpecifiedDirectivesPredicate)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

directive @Directive1(argument1: String = "stringValue1") on FIELD

type Object1 {
  field1: Object2
}

type Object2 {
  field2: String
}
"""
        newQuery == "{field1 @Directive1{field2 @Directive1}}"

    }

    def "query with directives with arguments"() {
        given:
        def schema = TestUtil.schema("""
        directive @whatever(myArg: String = "secret") on FIELD 
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        """)
        def query = '{foo @whatever(myArg: "secret2") {bar @whatever(myArg: "secret3") }}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(SchemaPrinter.ExcludeGraphQLSpecifiedDirectivesPredicate)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

directive @Directive1(argument1: String = "stringValue1") on FIELD

type Object1 {
  field1: Object2
}

type Object2 {
  field2: String
}
"""
        newQuery == '{field1 @Directive1(argument1:"stringValue2"){field2 @Directive1(argument1:"stringValue1")}}'

    }

    def "query with directives with arguments and variables"() {
        given:
        def schema = TestUtil.schema("""
        directive @whatever(myArg: String = "secret") on FIELD 
        type Query {
            foo: Foo
        }
        type Foo {
            bar(barArg: String): String
        }
        """)
        def query = 'query($myVar: String = "myDefaultValue"){foo @whatever(myArg: $myVar) {bar(barArg: "barArgValue") @whatever(myArg: "secret3") }}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(SchemaPrinter.ExcludeGraphQLSpecifiedDirectivesPredicate)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

directive @Directive1(argument1: String = "stringValue1") on FIELD

type Object1 {
  field1: Object2
}

type Object2 {
  field2(argument2: String): String
}
"""
        newQuery == 'query ($var1:String="stringValue3"){field1 @Directive1(argument1:$var1){field2(argument2:"stringValue2") @Directive1(argument1:"stringValue1")}}'

    }
}
