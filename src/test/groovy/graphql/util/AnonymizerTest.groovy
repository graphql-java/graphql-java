package graphql.util

import graphql.AssertException
import graphql.TestUtil
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
        newQuery == "query {field1 {field2 field3}}"
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
        newQuery == "query {...Fragment1 field1 {... on Object2 {field2 field3}}} fragment Fragment1 on Object1 {field1 {field2 field3}}"

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
        newQuery == 'query {field1(argument1:"stringValue1",argument2:"stringValue2") {field2 field3}}'

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
        newQuery == 'query operation($var1:ID) {field1(argument1:$var1,argument2:"stringValue1") {field2 field3(argument4:$var1)}}'


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
        }
        input MyInput {
            foo1: Int
            foo2: String = "myDefaultValue"
            foo3: Int = 1234
            foo4: Int = 4567 
        }
        """)
        def query = 'query myQuery($myVar: String = "someValue"){foo(myInput: {foo1: 8923, foo2: $myVar })}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1(argument1: InputObject1!): String
  field2(argument2: String = "defaultValue2"): String
}

input InputObject1 {
  inputField1: Int
  inputField2: String = "defaultValue1"
  inputField3: Int = 1
  inputField4: Int = 2
}
"""
        newQuery == 'query operation($var1:String="stringValue1") {field1(argument1:{foo1:1,foo2:$var1})}'


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
        newQuery == "query {alias1:field1 {alias2:field3}}"
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
        newQuery == "query {field2 {__typename alias1:__typename field1}}"
    }
}
