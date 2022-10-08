package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaChanges.*

class EditOperationAnalyzerTest extends Specification {

//    def "test field changed"() {
//        given:
//        def oldSdl = '''
//        type Query {
//            hello: String
//        }
//        '''
//        def newSdl = '''
//        type Query {
//            hello2: String
//        }
//        '''
//        when:
//        def changes = changes(oldSdl, newSdl)
//        then:
//        changes
//        (changes[0] as FieldModified).name == "hello2"
//        (changes[0] as FieldModified).fieldsContainer == "Query"
//    }
//
//    def "test field added"() {
//        given:
//        def oldSdl = '''
//        type Query {
//            hello: String
//        }
//        '''
//        def newSdl = '''
//        type Query {
//            hello: String
//            newOne: String
//        }
//        '''
//        when:
//        def changes = changes(oldSdl, newSdl)
//        then:
//        changes.size() == 1
//        (changes[0] as FieldAdded).name == "newOne"
//        (changes[0] as FieldAdded).fieldsContainer == "Query"
//    }

    def "test Object added"() {
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
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"]instanceof ObjectAdded
    }

    def "test new Interface introduced"() {
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
        changes.interfaceChanges["Node"] instanceof InterfaceAdded
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"] instanceof ObjectModified
        def addedInterfaceDetails = objectChanged[0].objectChangeDetails.findAll({ it instanceof ObjectModified.AddedInterfaceToObjectDetail }) as List<ObjectModified.AddedInterfaceToObjectDetail>
        addedInterfaceDetails.size() == 1
        addedInterfaceDetails[0].name == "Node"
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
        def interfaceChanged = changes.findAll({ it instanceof InterfaceModified }) as List<InterfaceModified>
        then:
        interfaceChanged.size() == 1
        interfaceChanged[0].name == "Node2"
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
        def interfaceChanged = changes.findAll({ it instanceof InterfaceModified }) as List<InterfaceModified>
        then:
        interfaceChanged.size() == 1
        interfaceChanged.interfaceChangeDetails.size() == 1
        (interfaceChanged.interfaceChangeDetails[0] as InterfaceModified.AddedInterfaceToInterfaceDetail).name == "NewI"
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
