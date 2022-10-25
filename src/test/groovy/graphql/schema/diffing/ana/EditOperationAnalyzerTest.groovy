package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaDifference.*

class EditOperationAnalyzerTest extends Specification {

    def "field renamed"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello2: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def fieldRenames = objectModification.getDetails(ObjectFieldRename.class)
        fieldRenames[0].oldName == "hello"
        fieldRenames[0].newName == "hello2"
    }

    def "union added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B 
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionAddition
        (changes.unionChanges["U"] as UnionAddition).name == "U"
    }

    def "union removed"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            hello: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionDeletion
        (changes.unionChanges["U"] as UnionDeletion).name == "U"
    }

    def "union member added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B | C
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        type C {
            foo: String
        } 

        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionModification
    }

    def "field type modified"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String!
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def typeModification = objectModification.getDetails(ObjectFieldTypeModification.class)
        typeModification[0].oldType == "String"
        typeModification[0].newType == "String!"
    }

    def "argument removed"() {
        given:
        def oldSdl = '''
        type Query {
            hello(arg: String): String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def argumentRemoved = objectModification.getDetails(ObjectFieldArgumentDeletion.class);
        argumentRemoved[0].fieldName == "hello"
        argumentRemoved[0].name == "arg"
    }

    def "argument default value modified for Object and Interface"() {
        given:
        def oldSdl = '''
        type Query implements Foo {
            foo(arg: String = "bar"): String
        }
        interface Foo {
            foo(arg: String = "bar"): String
        }
        
        '''
        def newSdl = '''
        type Query implements Foo {
            foo(arg: String = "barChanged"): String
        }
        interface Foo {
            foo(arg: String = "barChanged"): String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)

        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def objDefaultValueModified = objectModification.getDetails(ObjectFieldArgumentDefaultValueModification.class);
        objDefaultValueModified[0].fieldName == "foo"
        objDefaultValueModified[0].argumentName == "arg"
        objDefaultValueModified[0].oldValue == '"bar"'
        objDefaultValueModified[0].newValue == '"barChanged"'
        and:
        changes.interfaceChanges["Foo"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceChanges["Foo"] as InterfaceModification
        def intDefaultValueModified = interfaceModification.getDetails(InterfaceFieldArgumentDefaultValueModification.class);
        intDefaultValueModified[0].fieldName == "foo"
        intDefaultValueModified[0].argumentName == "arg"
        intDefaultValueModified[0].oldValue == '"bar"'
        intDefaultValueModified[0].newValue == '"barChanged"'
    }

    def "field added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
            newOne: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def fieldAdded = objectModification.getDetails(ObjectFieldAddition)
        fieldAdded[0].name == "newOne"
    }

    def "object added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
            foo: Foo
        }
        type Foo {
            id: ID
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Foo"] instanceof ObjectAddition
    }

    def "object removed and field type changed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Foo"] instanceof ObjectDeletion
        (changes.objectChanges["Foo"] as ObjectDeletion).name == "Foo"
        changes.objectChanges["Query"] instanceof ObjectModification
        def queryObjectModification = changes.objectChanges["Query"] as ObjectModification
        queryObjectModification.details.size() == 1
        queryObjectModification.details[0] instanceof ObjectFieldTypeModification
        (queryObjectModification.details[0] as ObjectFieldTypeModification).oldType == "Foo"
        (queryObjectModification.details[0] as ObjectFieldTypeModification).newType == "String"

    }

    def "new Interface introduced"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        type Foo {
          id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges.size() == 1
        changes.interfaceChanges["Node"] instanceof InterfaceAddition
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Foo"] as ObjectModification
        def addedInterfaceDetails = objectModification.getDetails(ObjectInterfaceImplementationAddition.class)
        addedInterfaceDetails.size() == 1
        addedInterfaceDetails[0].name == "Node"
    }

    def "Object and Interface added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges.size() == 1
        changes.interfaceChanges["Node"] instanceof InterfaceAddition
        changes.objectChanges.size() == 2
        changes.objectChanges["Foo"] instanceof ObjectAddition
        changes.objectChanges["Query"] instanceof ObjectModification
        (changes.objectChanges["Query"] as ObjectModification).getDetails()[0] instanceof ObjectFieldTypeModification
    }

    def "interfaced renamed"() {
        given:
        def oldSdl = '''
        type Query {
          foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface Node2 {
            id: ID!
        }
        type Foo implements Node2{
            id: ID!
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges.size() == 1
        changes.interfaceChanges["Node2"] instanceof InterfaceModification
    }

    def "interfaced renamed and another interface added to it"() {
        given:
        def oldSdl = '''
        type Query {
          foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface NewI {
            hello: String
        }
        interface Node2 {
            id: ID!
        }
        type Foo implements Node2 & NewI{
            id: ID!
            hello: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges.size() == 2
        changes.interfaceChanges["Node2"] instanceof InterfaceModification
        changes.interfaceChanges["NewI"] instanceof InterfaceAddition
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Foo"] as ObjectModification
        def addedInterfaceDetails = objectModification.getDetails(ObjectInterfaceImplementationAddition)
        addedInterfaceDetails.size() == 1
        addedInterfaceDetails[0].name == "NewI"

    }


    EditOperationAnalysisResult changes(
            String oldSdl,
            String newSdl
    ) {
        def oldSchema = TestUtil.schema(oldSdl)
        def newSchema = TestUtil.schema(newSdl)
        def changes = new SchemaDiffing().diffAndAnalyze(oldSchema, newSchema)
        return changes
    }
}
