package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaChanges.*

class EditOperationAnalyzerTest extends Specification {

    def "field name changed"() {
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
        changes.objectChanges["Query"] instanceof ObjectModified
        def objectModified = changes.objectChanges["Query"] as ObjectModified
        def fieldRenames = objectModified.objectChangeDetails.findAll({ it instanceof ObjectModified.FieldRenamed }) as List<ObjectModified.FieldRenamed>
        fieldRenames[0].oldName == "hello"
        fieldRenames[0].newName == "hello2"
    }
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
        changes.interfaceChanges["Node"] instanceof InterfaceAdded
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"] instanceof ObjectModified
        def objectModified = changes.objectChanges["Foo"] as ObjectModified
        def addedInterfaceDetails = objectModified.objectChangeDetails.findAll({ it instanceof ObjectModified.AddedInterfaceToObjectDetail }) as List<ObjectModified.AddedInterfaceToObjectDetail>
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
        changes.interfaceChanges["Node"] instanceof InterfaceAdded
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"] instanceof ObjectAdded
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
        changes.interfaceChanges["Node2"] instanceof InterfaceModified
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
        changes.interfaceChanges["Node2"] instanceof InterfaceModified
        changes.interfaceChanges["NewI"] instanceof InterfaceAdded
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"] instanceof ObjectModified
        def objectModified = changes.objectChanges["Foo"] as ObjectModified
        def addedInterfaceDetails = objectModified.objectChangeDetails.findAll({ it instanceof ObjectModified.AddedInterfaceToObjectDetail }) as List<ObjectModified.AddedInterfaceToObjectDetail>
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
