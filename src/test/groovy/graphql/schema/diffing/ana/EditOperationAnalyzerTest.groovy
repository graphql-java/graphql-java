package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.Edge
import graphql.schema.diffing.EditOperation
import graphql.schema.diffing.SchemaDiffing
import graphql.schema.diffing.SchemaGraph
import graphql.schema.diffing.Vertex
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveDeletion
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveDirectiveArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceFieldArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectFieldArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectFieldLocation
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveAddition
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentAddition
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentDefaultValueModification
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentDeletion
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentRename
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveArgumentTypeModification
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveDeletion
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveModification
import static graphql.schema.diffing.ana.SchemaDifference.EnumAddition
import static graphql.schema.diffing.ana.SchemaDifference.EnumDeletion
import static graphql.schema.diffing.ana.SchemaDifference.EnumModification
import static graphql.schema.diffing.ana.SchemaDifference.EnumValueAddition
import static graphql.schema.diffing.ana.SchemaDifference.EnumValueDeletion
import static graphql.schema.diffing.ana.SchemaDifference.EnumValueRenamed
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectAddition
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectDeletion
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldAddition
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldDefaultValueModification
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldDeletion
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldRename
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectFieldTypeModification
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectModification
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceAddition
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceDeletion
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldAddition
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentAddition
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentDefaultValueModification
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentDeletion
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentRename
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldArgumentTypeModification
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldDeletion
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldRename
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceFieldTypeModification
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceInterfaceImplementationDeletion
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceModification
import static graphql.schema.diffing.ana.SchemaDifference.ObjectAddition
import static graphql.schema.diffing.ana.SchemaDifference.ObjectDeletion
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldAddition
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentAddition
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentDefaultValueModification
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentDeletion
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentRename
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldArgumentTypeModification
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldDeletion
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldRename
import static graphql.schema.diffing.ana.SchemaDifference.ObjectFieldTypeModification
import static graphql.schema.diffing.ana.SchemaDifference.ObjectInterfaceImplementationAddition
import static graphql.schema.diffing.ana.SchemaDifference.ObjectInterfaceImplementationDeletion
import static graphql.schema.diffing.ana.SchemaDifference.ObjectModification
import static graphql.schema.diffing.ana.SchemaDifference.ScalarAddition
import static graphql.schema.diffing.ana.SchemaDifference.ScalarDeletion
import static graphql.schema.diffing.ana.SchemaDifference.ScalarModification
import static graphql.schema.diffing.ana.SchemaDifference.UnionAddition
import static graphql.schema.diffing.ana.SchemaDifference.UnionDeletion
import static graphql.schema.diffing.ana.SchemaDifference.UnionMemberAddition
import static graphql.schema.diffing.ana.SchemaDifference.UnionMemberDeletion
import static graphql.schema.diffing.ana.SchemaDifference.UnionModification

class EditOperationAnalyzerTest extends Specification {
    def "object renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        schema {
         query: MyQuery 
        }
        type MyQuery {
            foo: String
        }
         
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] === changes.objectDifferences["MyQuery"]
        changes.objectDifferences["Query"] instanceof ObjectModification
        (changes.objectDifferences["Query"] as ObjectModification).oldName == "Query"
        (changes.objectDifferences["Query"] as ObjectModification).newName == "MyQuery"
        (changes.objectDifferences["Query"] as ObjectModification).isNameChanged()
    }

    def "interface renamed"() {
        given:
        def oldSdl = '''
        type Query implements I {
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements IRenamed {
            foo: String
        }
        interface IRenamed {
            foo: String
        }
         
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] === changes.interfaceDifferences["IRenamed"]
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        (changes.interfaceDifferences["I"] as InterfaceModification).oldName == "I"
        (changes.interfaceDifferences["I"] as InterfaceModification).newName == "IRenamed"
        (changes.interfaceDifferences["I"] as InterfaceModification).isNameChanged()
    }

    def "interface removed from object"() {
        given:
        def oldSdl = '''
        type Query implements I {
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query{
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def implementationDeletions = (changes.objectDifferences["Query"] as ObjectModification).getDetails(ObjectInterfaceImplementationDeletion)
        implementationDeletions[0].name == "I"
    }

    def "interface removed from interface"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        interface FooI {
            foo: String
        }
        interface Foo implements FooI {
            foo: String
        }
        type FooImpl implements Foo & FooI {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface Foo {
            foo: String
        }
        type FooImpl implements Foo {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["Foo"] instanceof InterfaceModification
        def implementationDeletions = (changes.interfaceDifferences["Foo"] as InterfaceModification).getDetails(InterfaceInterfaceImplementationDeletion)
        implementationDeletions[0].name == "FooI"
    }

    def "object and interface field renamed"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello: String
        }
        interface I {
            hello: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            hello2: String
        }
        interface I {
            hello2: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldRenames = objectModification.getDetails(ObjectFieldRename.class)
        oFieldRenames[0].oldName == "hello"
        oFieldRenames[0].newName == "hello2"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldRenames = interfaceModification.getDetails(InterfaceFieldRename.class)
        iFieldRenames[0].oldName == "hello"
        iFieldRenames[0].newName == "hello2"
    }

    def "object and interface field deleted"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello: String
            toDelete: String
        }
        interface I {
            hello: String
            toDelete: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            hello: String
        }
        interface I {
            hello: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldDeletions = objectModification.getDetails(ObjectFieldDeletion.class)
        oFieldDeletions[0].name == "toDelete"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldDeletions = interfaceModification.getDetails(InterfaceFieldDeletion.class)
        iFieldDeletions[0].name == "toDelete"

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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionAddition
        (changes.unionDifferences["U"] as UnionAddition).name == "U"
    }

    def "union deleted"() {
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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionDeletion
        (changes.unionDifferences["U"] as UnionDeletion).name == "U"
    }

    def "union renamed"() {
        given:
        def oldSdl = '''
        type Query {
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
            u: X 
        }
        union X = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["X"] === changes.unionDifferences["U"]
        changes.unionDifferences["U"] instanceof UnionModification
        (changes.unionDifferences["U"] as UnionModification).oldName == "U"
        (changes.unionDifferences["U"] as UnionModification).newName == "X"
        (changes.unionDifferences["U"] as UnionModification).isNameChanged()
    }

    def "union renamed and member removed"() {
        given:
        def oldSdl = '''
        type Query {
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
            u: X 
        }
        union X = A 
        type A {
            foo: String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def unionDiff = changes.unionDifferences["U"] as UnionModification
        unionDiff.oldName == "U"
        unionDiff.newName == "X"
        unionDiff.getDetails(UnionMemberDeletion)[0].name == "B"
        unionDiff.isNameChanged()
    }

    def "union renamed and member added"() {
        given:
        def oldSdl = '''
        type Query {
            u: U 
        }
        union U = A 
        type A {
            foo: String
        } 

        '''
        def newSdl = '''
        type Query {
            u: X
        }
        union X = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def unionDiff = changes.unionDifferences["U"] as UnionModification
        unionDiff.oldName == "U"
        unionDiff.newName == "X"
        unionDiff.isNameChanged()
        unionDiff.getDetails(UnionMemberAddition)[0].name == "B"
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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def unionModification = changes.unionDifferences["U"] as UnionModification
        unionModification.getDetails(UnionMemberAddition)[0].name == "C"
    }

    def "union member deleted"() {
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
        union U = A 
        type A {
            foo: String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def unionModification = changes.unionDifferences["U"] as UnionModification
        unionModification.getDetails(UnionMemberDeletion)[0].name == "B"
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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def typeModification = objectModification.getDetails(ObjectFieldTypeModification.class)
        typeModification[0].oldType == "String"
        typeModification[0].newType == "String!"
    }

    def "object and interface field argument added"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello: String
        }
        interface I {
            hello: String
        } 
        '''
        def newSdl = '''
        type Query implements I{
            hello(arg: String): String
        }
        interface I {
            hello(arg: String): String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def objectArgumentAdded = objectModification.getDetails(ObjectFieldArgumentAddition.class);
        objectArgumentAdded[0].name == "arg"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["I"] as InterfaceModification
        def interfaceArgumentAdded = interfaceModification.getDetails(InterfaceFieldArgumentAddition.class);
        interfaceArgumentAdded[0].name == "arg"

    }

    def "object and interface field argument renamed"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello(arg: String): String
        }
        interface I {
            hello(arg: String): String
        } 
        '''
        def newSdl = '''
        type Query implements I{
            hello(argRename: String): String
        }
        interface I {
            hello(argRename: String): String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def objectArgumentRenamed = objectModification.getDetails(ObjectFieldArgumentRename.class);
        objectArgumentRenamed[0].fieldName == "hello"
        objectArgumentRenamed[0].oldName == "arg"
        objectArgumentRenamed[0].newName == "argRename"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["I"] as InterfaceModification
        def interfaceArgumentRenamed = interfaceModification.getDetails(InterfaceFieldArgumentRename.class);
        interfaceArgumentRenamed[0].fieldName == "hello"
        interfaceArgumentRenamed[0].oldName == "arg"
        interfaceArgumentRenamed[0].newName == "argRename"
    }


    def "object and interface field argument deleted"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello(arg: String): String
        }
        interface I{
            hello(arg: String): String
        }
        '''
        def newSdl = '''
        type Query implements I {
            hello: String
        }
        interface I {
            hello: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def oArgumentRemoved = objectModification.getDetails(ObjectFieldArgumentDeletion.class);
        oArgumentRemoved[0].fieldName == "hello"
        oArgumentRemoved[0].name == "arg"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iArgumentRemoved = interfaceModification.getDetails(InterfaceFieldArgumentDeletion.class);
        iArgumentRemoved[0].fieldName == "hello"
        iArgumentRemoved[0].name == "arg"

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
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def objDefaultValueModified = objectModification.getDetails(ObjectFieldArgumentDefaultValueModification.class);
        objDefaultValueModified[0].fieldName == "foo"
        objDefaultValueModified[0].argumentName == "arg"
        objDefaultValueModified[0].oldValue == '"bar"'
        objDefaultValueModified[0].newValue == '"barChanged"'
        and:
        changes.interfaceDifferences["Foo"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["Foo"] as InterfaceModification
        def intDefaultValueModified = interfaceModification.getDetails(InterfaceFieldArgumentDefaultValueModification.class);
        intDefaultValueModified[0].fieldName == "foo"
        intDefaultValueModified[0].argumentName == "arg"
        intDefaultValueModified[0].oldValue == '"bar"'
        intDefaultValueModified[0].newValue == '"barChanged"'
    }

    def "object and interface argument type changed completely"() {
        given:
        def oldSdl = '''
        type Query implements Foo {
            foo(arg: String ): String
        }
        interface Foo {
            foo(arg: String): String
        }
        
        '''
        def newSdl = '''
        type Query implements Foo {
            foo(arg: Int!): String
        }
        interface Foo {
            foo(arg: Int!): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def objDefaultValueModified = objectModification.getDetails(ObjectFieldArgumentTypeModification.class);
        objDefaultValueModified[0].fieldName == "foo"
        objDefaultValueModified[0].argumentName == "arg"
        objDefaultValueModified[0].oldType == 'String'
        objDefaultValueModified[0].newType == 'Int!'
        and:
        changes.interfaceDifferences["Foo"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["Foo"] as InterfaceModification
        def intDefaultValueModified = interfaceModification.getDetails(InterfaceFieldArgumentTypeModification.class);
        intDefaultValueModified[0].fieldName == "foo"
        intDefaultValueModified[0].argumentName == "arg"
        intDefaultValueModified[0].oldType == 'String'
        intDefaultValueModified[0].newType == 'Int!'
    }

    def "object and interface argument type changed wrapping type"() {
        given:
        def oldSdl = '''
        type Query implements Foo {
            foo(arg: String ): String
        }
        interface Foo {
            foo(arg: String): String
        }
        
        '''
        def newSdl = '''
        type Query implements Foo {
            foo(arg: String!): String
        }
        interface Foo {
            foo(arg: String!): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def objDefaultValueModified = objectModification.getDetails(ObjectFieldArgumentTypeModification.class);
        objDefaultValueModified[0].fieldName == "foo"
        objDefaultValueModified[0].argumentName == "arg"
        objDefaultValueModified[0].oldType == 'String'
        objDefaultValueModified[0].newType == 'String!'
        and:
        changes.interfaceDifferences["Foo"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["Foo"] as InterfaceModification
        def intDefaultValueModified = interfaceModification.getDetails(InterfaceFieldArgumentTypeModification.class);
        intDefaultValueModified[0].fieldName == "foo"
        intDefaultValueModified[0].argumentName == "arg"
        intDefaultValueModified[0].oldType == 'String'
        intDefaultValueModified[0].newType == 'String!'
    }

    def "object and interface field added"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello: String
        }
        interface I {
            hello: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            hello: String
            newOne: String
        }
        interface I {
            hello: String
            newOne: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldAdded = objectModification.getDetails(ObjectFieldAddition)
        oFieldAdded[0].name == "newOne"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def iInterfaces = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldAdded = iInterfaces.getDetails(InterfaceFieldAddition)
        iFieldAdded[0].name == "newOne"

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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Foo"] instanceof ObjectAddition
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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Foo"] instanceof ObjectDeletion
        (changes.objectDifferences["Foo"] as ObjectDeletion).name == "Foo"
        changes.objectDifferences["Query"] instanceof ObjectModification
        def queryObjectModification = changes.objectDifferences["Query"] as ObjectModification
        queryObjectModification.details.size() == 1
        queryObjectModification.details[0] instanceof ObjectFieldTypeModification
        (queryObjectModification.details[0] as ObjectFieldTypeModification).oldType == "Foo"
        (queryObjectModification.details[0] as ObjectFieldTypeModification).newType == "String"

    }

    def "Interface and Object field type changed completely"() {
        given:
        def oldSdl = '''
        type Query implements I{
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            foo: ID
        }
        interface I {
            foo: ID
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def iModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldTypeModifications = iModification.getDetails(InterfaceFieldTypeModification)
        iFieldTypeModifications[0].fieldName == "foo"
        iFieldTypeModifications[0].oldType == "String"
        iFieldTypeModifications[0].newType == "ID"
        and:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def oModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldTypeModifications = oModification.getDetails(ObjectFieldTypeModification)
        oFieldTypeModifications[0].fieldName == "foo"
        oFieldTypeModifications[0].oldType == "String"
        oFieldTypeModifications[0].newType == "ID"
    }

    def "Interface and Object field type changed wrapping type"() {
        given:
        def oldSdl = '''
        type Query implements I{
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            foo: [String!]
        }
        interface I {
            foo: [String!]
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def iModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldTypeModifications = iModification.getDetails(InterfaceFieldTypeModification)
        iFieldTypeModifications[0].fieldName == "foo"
        iFieldTypeModifications[0].oldType == "String"
        iFieldTypeModifications[0].newType == "[String!]"
        and:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def oModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldTypeModifications = oModification.getDetails(ObjectFieldTypeModification)
        oFieldTypeModifications[0].fieldName == "foo"
        oFieldTypeModifications[0].oldType == "String"
        oFieldTypeModifications[0].newType == "[String!]"


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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences.size() == 1
        changes.interfaceDifferences["Node"] instanceof InterfaceAddition
        changes.objectDifferences.size() == 1
        changes.objectDifferences["Foo"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Foo"] as ObjectModification
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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences.size() == 1
        changes.interfaceDifferences["Node"] instanceof InterfaceAddition
        changes.objectDifferences.size() == 2
        changes.objectDifferences["Foo"] instanceof ObjectAddition
        changes.objectDifferences["Query"] instanceof ObjectModification
        (changes.objectDifferences["Query"] as ObjectModification).getDetails()[0] instanceof ObjectFieldTypeModification
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
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences.size() == 2
        changes.interfaceDifferences["Node"] === changes.interfaceDifferences["Node2"]
        changes.interfaceDifferences["Node2"] instanceof InterfaceModification
        (changes.interfaceDifferences["Node2"] as InterfaceModification).isNameChanged()
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
        type Foo implements Node2 & NewI {
            id: ID!
            hello: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences.size() == 3
        changes.interfaceDifferences["Node"] == changes.interfaceDifferences["Node2"]
        changes.interfaceDifferences["Node2"] instanceof InterfaceModification
        (changes.interfaceDifferences["Node2"] as InterfaceModification).isNameChanged()
        changes.interfaceDifferences["NewI"] instanceof InterfaceAddition
        changes.objectDifferences.size() == 1
        changes.objectDifferences["Foo"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Foo"] as ObjectModification
        def addedInterfaceDetails = objectModification.getDetails(ObjectInterfaceImplementationAddition)
        addedInterfaceDetails.size() == 1
        addedInterfaceDetails[0].name == "NewI"
    }

    def "enum renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        enum E {
            A, B
        }
        '''
        def newSdl = '''
        type Query {
            foo: ERenamed
        }
        enum ERenamed {
            A, B
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] === changes.enumDifferences["ERenamed"]
        def modification = changes.enumDifferences["E"] as EnumModification
        modification.oldName == "E"
        modification.newName == "ERenamed"
        modification.isNameChanged()
    }

    def "enum added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: E
        }
        enum E {
            A, B
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumAddition
        (changes.enumDifferences["E"] as EnumAddition).getName() == "E"
    }

    def "enum deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: E
        }
        enum E {
            A, B
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumDeletion
        (changes.enumDifferences["E"] as EnumDeletion).getName() == "E"
    }


    def "enum value added"() {
        given:
        def oldSdl = '''
        type Query {
            e: E
        }
        enum E {
            A
        }
        '''
        def newSdl = '''
        type Query {
            e: E
        }
        enum E {
            A, B
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def enumModification = changes.enumDifferences["E"] as EnumModification
        enumModification.getDetails(EnumValueAddition)[0].name == "B"
    }

    def "enum value deleted"() {
        given:
        def oldSdl = '''
        type Query {
            e: E
        }
        enum E {
            A,B
        }
        '''
        def newSdl = '''
        type Query {
            e: E
        }
        enum E {
            A
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def enumModification = changes.enumDifferences["E"] as EnumModification
        enumModification.getDetails(EnumValueDeletion)[0].name == "B"
    }

    def "enum value added and removed"() {
        given:
        def oldSdl = '''
        type Query {
            e: MyEnum
        }
        enum MyEnum {
            A
            B
        }
        '''
        def newSdl = '''
        type Query {
            e: MyEnum
        }
        enum MyEnum {
            A
            C
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["MyEnum"] instanceof EnumModification

        def enumModification = changes.enumDifferences["MyEnum"] as EnumModification
        enumModification.getDetails().size() == 1

        def rename = enumModification.getDetails(EnumValueRenamed)[0]
        rename.oldName == "B"
        rename.newName == "C"
    }

    def "scalar added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: E
        }
        scalar E
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["E"] instanceof ScalarAddition
        (changes.scalarDifferences["E"] as ScalarAddition).getName() == "E"
    }

    def "scalar deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: E
        }
        scalar E
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["E"] instanceof ScalarDeletion
        (changes.scalarDifferences["E"] as ScalarDeletion).getName() == "E"
    }

    def "scalar renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        scalar Foo
        '''
        def newSdl = '''
        type Query {
            foo: Bar
        }
        scalar Bar
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["Foo"] === changes.scalarDifferences["Bar"]
        def modification = changes.scalarDifferences["Foo"] as ScalarModification
        modification.oldName == "Foo"
        modification.newName == "Bar"
        modification.isNameChanged()
    }

    def "input object added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectAddition
        (changes.inputObjectDifferences["I"] as InputObjectAddition).getName() == "I"
    }

    def "input object field added"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
            newField: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def modification = changes.inputObjectDifferences["I"] as InputObjectModification
        def fieldAddition = modification.getDetails(InputObjectFieldAddition)[0]
        fieldAddition.name == "newField"
    }

    def "input object field deletion"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
            toDelete: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def modification = changes.inputObjectDifferences["I"] as InputObjectModification
        def fieldDeletion = modification.getDetails(InputObjectFieldDeletion)[0]
        fieldDeletion.name == "toDelete"
    }

    def "input object field renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            barNew: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def modification = changes.inputObjectDifferences["I"] as InputObjectModification
        def fieldDeletion = modification.getDetails(InputObjectFieldRename)[0]
        fieldDeletion.oldName == "bar"
        fieldDeletion.newName == "barNew"
    }

    def "input object field wrapping type changed"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: [String]
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def modification = changes.inputObjectDifferences["I"] as InputObjectModification
        def typeModification = modification.getDetails(InputObjectFieldTypeModification)[0]
        typeModification.oldType == "String"
        typeModification.newType == "[String]"
    }

    def "input object field type changed"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: ID
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def modification = changes.inputObjectDifferences["I"] as InputObjectModification
        def typeModification = modification.getDetails(InputObjectFieldTypeModification)[0]
        typeModification.oldType == "String"
        typeModification.newType == "ID"
    }

    def "input object field default value changed"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String = "A"
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String = "B"
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def modification = changes.inputObjectDifferences["I"] as InputObjectModification
        def modificationDetail = modification.getDetails(InputObjectFieldDefaultValueModification)[0]
        modificationDetail.oldDefaultValue == '"A"'
        modificationDetail.newDefaultValue == '"B"'
    }

    def "input object deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectDeletion
        (changes.inputObjectDifferences["I"] as InputObjectDeletion).getName() == "I"
    }

    def "input object renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String 
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: IRenamed): String 
        }
        input IRenamed {
            bar: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] === changes.inputObjectDifferences["IRenamed"]
        def modification = changes.inputObjectDifferences["I"] as InputObjectModification
        modification.oldName == "I"
        modification.newName == "IRenamed"
        modification.isNameChanged()
    }


    def "directive added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveAddition
        (changes.directiveDifferences["d"] as DirectiveAddition).getName() == "d"
    }

    def "directive deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveDeletion
        (changes.directiveDifferences["d"] as DirectiveDeletion).getName() == "d"
    }

    def "directive renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @dRenamed on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] === changes.directiveDifferences["dRenamed"]
        def modification = changes.directiveDifferences["d"] as DirectiveModification
        modification.oldName == "d"
        modification.newName == "dRenamed"
        modification.isNameChanged()
    }

    def "directive argument renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo: String) on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d(bar:String) on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveModification
        def renames = (changes.directiveDifferences["d"] as DirectiveModification).getDetails(DirectiveArgumentRename)
        renames[0].oldName == "foo"
        renames[0].newName == "bar"
    }

    def "directive argument added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo:String) on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveModification
        def addition = (changes.directiveDifferences["d"] as DirectiveModification).getDetails(DirectiveArgumentAddition)
        addition[0].name == "foo"


    }

    def "directive argument deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo:String) on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveModification
        def deletion = (changes.directiveDifferences["d"] as DirectiveModification).getDetails(DirectiveArgumentDeletion)
        deletion[0].name == "foo"


    }

    def "directive argument default value changed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo:String = "A") on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo: String = "B") on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveModification
        def defaultValueChange = (changes.directiveDifferences["d"] as DirectiveModification).getDetails(DirectiveArgumentDefaultValueModification)
        defaultValueChange[0].argumentName == "foo"
        defaultValueChange[0].oldValue == '"A"'
        defaultValueChange[0].newValue == '"B"'


    }

    def "directive argument type changed completely"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo:String) on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo: ID)  on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveModification
        def argTypeModification = (changes.directiveDifferences["d"] as DirectiveModification).getDetails(DirectiveArgumentTypeModification)
        argTypeModification[0].argumentName == "foo"
        argTypeModification[0].oldType == 'String'
        argTypeModification[0].newType == 'ID'
    }

    def "directive argument wrapping type changed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo:String) on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo: [String]!)  on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveModification
        def argTypeModification = (changes.directiveDifferences["d"] as DirectiveModification).getDetails(DirectiveArgumentTypeModification)
        argTypeModification[0].argumentName == "foo"
        argTypeModification[0].oldType == 'String'
        argTypeModification[0].newType == '[String]!'
    }

    def "field renamed and output type changed and argument deleted"() {
        given:
        def oldSdl = '''
        type Query {
            ping(pong: String): ID
        }
        '''
        def newSdl = '''
        type Query {
            echo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        true
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectDiff = changes.objectDifferences["Query"] as ObjectModification

        def rename = objectDiff.getDetails(ObjectFieldRename)
        rename.size() == 1
        rename[0].oldName == "ping"
        rename[0].newName == "echo"

        def argumentDeletion = objectDiff.getDetails(ObjectFieldArgumentDeletion)
        argumentDeletion.size() == 1
        argumentDeletion[0].fieldName == "ping"
        argumentDeletion[0].name == "pong"

        def typeModification = objectDiff.getDetails(ObjectFieldTypeModification)
        typeModification.size() == 1
        typeModification[0].fieldName == "echo"
        typeModification[0].oldType == "ID"
        typeModification[0].newType == "String"
    }

    def "object field argument changed and applied directive deleted"() {
        given:
        def oldSdl = '''
        type Query {
            ping(pong: String @d): ID
        }
        directive @d on ARGUMENT_DEFINITION
        '''
        def newSdl = '''
        type Query {
            ping(pong: Int): ID
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        true
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectDiff = changes.objectDifferences["Query"] as ObjectModification

        def typeModification = objectDiff.getDetails(ObjectFieldArgumentTypeModification)
        typeModification.size() == 1
        typeModification[0].oldType == "String"
        typeModification[0].newType == "Int"
        typeModification[0].fieldName == "ping"
        typeModification[0].argumentName == "pong"

        def directiveDeletion = objectDiff.getDetails(AppliedDirectiveDeletion)
        directiveDeletion.size() == 1
        directiveDeletion[0].name == "d"
        directiveDeletion[0].locationDetail instanceof AppliedDirectiveObjectFieldArgumentLocation

        def location = directiveDeletion[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation
        location.objectName == "Query"
        location.fieldName == "ping"
        location.argumentName == "pong"
    }

    def "interface field argument changed and applied directive deleted"() {
        given:
        def oldSdl = '''
        type Query {
            echo: String
        }
        interface TableTennis {
            ping(pong: String @d): ID
        }
        directive @d on ARGUMENT_DEFINITION
        '''
        def newSdl = '''
        type Query {
            echo: String
        }
        interface TableTennis {
            ping(pong: Int): ID
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        true
        changes.interfaceDifferences["TableTennis"] instanceof InterfaceModification
        def diff = changes.interfaceDifferences["TableTennis"] as InterfaceModification

        def typeModification = diff.getDetails(InterfaceFieldArgumentTypeModification)
        typeModification.size() == 1
        typeModification[0].oldType == "String"
        typeModification[0].newType == "Int"
        typeModification[0].fieldName == "ping"
        typeModification[0].argumentName == "pong"

        def directiveDeletion = diff.getDetails(AppliedDirectiveDeletion)
        directiveDeletion.size() == 1
        directiveDeletion[0].name == "d"
        directiveDeletion[0].locationDetail instanceof AppliedDirectiveInterfaceFieldArgumentLocation

        def location = directiveDeletion[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation
        location.interfaceName == "TableTennis"
        location.fieldName == "ping"
        location.argumentName == "pong"
    }

    def "directive argument changed and applied directive deleted"() {
        given:
        def oldSdl = '''
        type Query {
            ping(pong: String): ID @d
        }
        directive @a on ARGUMENT_DEFINITION
        directive @d(message: ID @a) on FIELD_DEFINITION
        '''
        def newSdl = '''
        type Query {
            ping(pong: String): ID @d
        }
        directive @a on ARGUMENT_DEFINITION
        directive @d(message: String) on FIELD_DEFINITION
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        true
        changes.directiveDifferences["d"] instanceof DirectiveModification
        def diff = changes.directiveDifferences["d"] as DirectiveModification

        def typeModification = diff.getDetails(DirectiveArgumentTypeModification)
        typeModification.size() == 1
        typeModification[0].oldType == "ID"
        typeModification[0].newType == "String"
        typeModification[0].argumentName == "message"

        def directiveDeletion = diff.getDetails(AppliedDirectiveDeletion)
        directiveDeletion.size() == 1
        directiveDeletion[0].name == "a"
        directiveDeletion[0].locationDetail instanceof AppliedDirectiveDirectiveArgumentLocation

        def location = directiveDeletion[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation
        location.directiveName == "d"
        location.argumentName == "message"
    }

    def "field output type changed and applied directive removed"() {
        given:
        def oldSdl = '''
        type Query {
            echo: ID @d
        }
        directive @d on FIELD_DEFINITION
        '''
        def newSdl = '''
        type Query {
            echo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        true
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectDiff = changes.objectDifferences["Query"] as ObjectModification

        def typeModification = objectDiff.getDetails(ObjectFieldTypeModification)
        typeModification.size() == 1
        typeModification[0].fieldName == "echo"
        typeModification[0].oldType == "ID"
        typeModification[0].newType == "String"

        def directiveDeletion = objectDiff.getDetails(AppliedDirectiveDeletion)
        directiveDeletion.size() == 1
        directiveDeletion[0].name == "d"
        directiveDeletion[0].locationDetail instanceof AppliedDirectiveObjectFieldLocation

        def location = directiveDeletion[0].locationDetail as AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "echo"
    }

    def "input field renamed and type changed and applied directive removed"() {
        given:
        def oldSdl = '''
        type Query {
            echo(input: Echo): String
        }
        input Echo {
            message: String @d
        }
        directive @d on INPUT_FIELD_DEFINITION
        '''
        def newSdl = '''
        type Query {
            echo(input: Echo): String
        }
        input Echo {
            age: Int
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["Echo"] instanceof InputObjectModification
        def diff = changes.inputObjectDifferences["Echo"] as InputObjectModification

        def rename = diff.getDetails(InputObjectFieldRename)
        rename.size() == 1
        rename[0].oldName == "message"
        rename[0].newName == "age"

        def typeModification = diff.getDetails(InputObjectFieldTypeModification)
        typeModification.size() == 1
        typeModification[0].fieldName == "age"
        typeModification[0].oldType == "String"
        typeModification[0].newType == "Int"

        def directiveDeletion = diff.getDetails(AppliedDirectiveDeletion)
        directiveDeletion.size() == 1
        directiveDeletion[0].name == "d"
    }

    def "object field description changed"() {
        given:
        def oldSdl = '''
        type Query {
            " Hello"
            echo: String
        }
        '''
        def newSdl = '''
        type Query {
            "Test "
            echo: String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        // no changes
        changes.objectDifferences["Query"] == null
    }

    def "interface field description changed"() {
        given:
        def oldSdl = '''
        type Query {
            node: Node
        }
        interface Node {
            " Hello"
            echo: String
        }
        '''
        def newSdl = '''
        type Query {
            node: Node
        }
        interface Node {
            "World"
            echo: String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        // no changes
        changes.interfaceDifferences["Node"] == null
    }

    def "interface deleted with field argument"() {
        given:
        def oldSdl = '''
        type Query {
            node: Node
        }
        interface Node {
            echo(test: String): String
        }
        '''
        def newSdl = '''
        type Query {
            node: ID
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.interfaceDifferences["Node"] instanceof InterfaceDeletion
    }

    def "object deleted with field argument"() {
        given:
        def oldSdl = '''
        type Query {
            node: Node
        }
        type Node {
            echo(test: String): String
        }
        '''
        def newSdl = '''
        type Query {
            node: ID
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["Node"] instanceof ObjectDeletion
    }

    def "directive deleted with argument"() {
        given:
        def oldSdl = '''
        type Query {
            node: String
        }
        directive @test(message: String) on FIELD
        '''
        def newSdl = '''
        type Query {
            node: String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.directiveDifferences["test"] instanceof DirectiveDeletion
    }

    def "interface added with field argument"() {
        given:
        def oldSdl = '''
        type Query {
            node: ID
        }
        '''
        def newSdl = '''
        type Query {
            node: Node
        }
        interface Node {
            echo(test: String): String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.interfaceDifferences["Node"] instanceof InterfaceAddition
    }

    def "object added with field argument"() {
        given:
        def oldSdl = '''
        type Query {
            node: ID
        }
        '''
        def newSdl = '''
        type Query {
            node: Node
        }
        type Node {
            echo(test: String): String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["Node"] instanceof ObjectAddition
    }

    def "directive added with argument"() {
        given:
        def oldSdl = '''
        type Query {
            node: String
        }
        '''
        def newSdl = '''
        type Query {
            node: String
        }
        directive @test(message: String) on FIELD
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.directiveDifferences["test"] instanceof DirectiveAddition
    }

    def "delete object with applied directive on field"() {
        given:
        def oldSdl = '''
        type Query {
            user(id: ID!): User
        }
        directive @id(type: String, owner: String) on FIELD_DEFINITION
        type User {
            id: ID! @id(type: "user", owner: "profiles")
        }
        '''
        def newSdl = '''
        type Query {
            echo: String
        }
        directive @id(type: String, owner: String) on FIELD_DEFINITION
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["User"] instanceof ObjectDeletion
    }

    def "delete interface with applied directive on field"() {
        given:
        def oldSdl = '''
        type Query {
            user(id: ID!): User
        }
        directive @id(type: String, owner: String) on FIELD_DEFINITION
        interface User {
            id: ID! @id(type: "user", owner: "profiles")
        }
        '''
        def newSdl = '''
        type Query {
            echo: String
        }
        directive @id(type: String, owner: String) on FIELD_DEFINITION
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.interfaceDifferences["User"] instanceof InterfaceDeletion
    }

    def "argument removed and similar argument added on separate object fields"() {
        given:
        def oldSdl = '''
        type Query {
            issues: IssueQuery
        }
        type IssueQuery {
            issue: Issue
            issues(id: [ID!]!): [Issue]
        }
        type Issue {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            issues: IssueQuery
        }
        type IssueQuery {
            issue(id: ID): Issue
            issues: [Issue]
        }
        type Issue {
            id: ID!
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["IssueQuery"] instanceof ObjectModification
        def issueQueryChanges = changes.objectDifferences["IssueQuery"] as ObjectModification
        issueQueryChanges.details.size() == 2

        def argumentAddition = issueQueryChanges.getDetails(ObjectFieldArgumentAddition)
        argumentAddition.size() == 1
        argumentAddition[0].fieldName == "issue"
        argumentAddition[0].name == "id"

        def argumentDeletion = issueQueryChanges.getDetails(ObjectFieldArgumentDeletion)
        argumentDeletion.size() == 1
        argumentDeletion[0].fieldName == "issues"
        argumentDeletion[0].name == "id"
    }

    def "argument removed and similar argument added on separate interface fields"() {
        given:
        def oldSdl = '''
        type Query {
            issues: IssueQuery
        }
        interface IssueQuery {
            issue: Issue
            issues(id: [ID!]!): [Issue]
        }
        type Issue {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            issues: IssueQuery
        }
        interface IssueQuery {
            issue(id: ID): Issue
            issues: [Issue]
        }
        type Issue {
            id: ID!
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.interfaceDifferences["IssueQuery"] instanceof InterfaceModification
        def issueQueryChanges = changes.interfaceDifferences["IssueQuery"] as InterfaceModification
        issueQueryChanges.details.size() == 2

        def argumentAddition = issueQueryChanges.getDetails(InterfaceFieldArgumentAddition)
        argumentAddition.size() == 1
        argumentAddition[0].fieldName == "issue"
        argumentAddition[0].name == "id"

        def argumentDeletion = issueQueryChanges.getDetails(InterfaceFieldArgumentDeletion)
        argumentDeletion.size() == 1
        argumentDeletion[0].fieldName == "issues"
        argumentDeletion[0].name == "id"
    }

    def "argument removed and similar argument added on separate directives"() {
        given:
        def oldSdl = '''
        directive @dog(name: String) on FIELD_DEFINITION
        directive @cat on FIELD_DEFINITION
        type Query {
            pet: String @dog
        }
        '''
        def newSdl = '''
        directive @dog on FIELD_DEFINITION
        directive @cat(name: [String]) on FIELD_DEFINITION
        type Query {
            pet: String @dog
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.directiveDifferences["cat"] instanceof DirectiveModification
        def catChanges = changes.directiveDifferences["cat"] as DirectiveModification
        catChanges.details.size() == 1
        def argumentAdditions = catChanges.getDetails(DirectiveArgumentAddition)
        argumentAdditions.size() == 1
        argumentAdditions[0].name == "name"

        changes.directiveDifferences["dog"] instanceof DirectiveModification
        def dogChanges = changes.directiveDifferences["dog"] as DirectiveModification
        dogChanges.details.size() == 1
        def argumentDeletions = dogChanges.getDetails(DirectiveArgumentDeletion)
        argumentDeletions.size() == 1
        argumentDeletions[0].name == "name"
    }

    def "argument removed and added on renamed object field"() {
        given:
        def oldSdl = '''
        type Query {
            issues: IssueQuery
        }
        type IssueQuery {
            issues(id: [ID!]): [Issue]
        }
        type Issue {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            issues: IssueQuery
        }
        type IssueQuery {
            issuesById(ids: [ID!]!): [Issue]
        }
        type Issue {
            id: ID!
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["IssueQuery"] instanceof ObjectModification
        def issueQueryChanges = changes.objectDifferences["IssueQuery"] as ObjectModification
        issueQueryChanges.details.size() == 3

        def rename = issueQueryChanges.getDetails(ObjectFieldRename)
        rename.size() == 1
        rename[0].oldName == "issues"
        rename[0].newName == "issuesById"

        def argumentRename = issueQueryChanges.getDetails(ObjectFieldArgumentRename)
        argumentRename.size() == 1
        argumentRename[0].fieldName == "issuesById"
        argumentRename[0].oldName == "id"
        argumentRename[0].newName == "ids"

        def argumentTypeModification = issueQueryChanges.getDetails(ObjectFieldArgumentTypeModification)
        argumentTypeModification.size() == 1
        argumentTypeModification[0].fieldName == "issuesById"
        argumentTypeModification[0].argumentName == "ids"
        argumentTypeModification[0].oldType == "[ID!]"
        argumentTypeModification[0].newType == "[ID!]!"
    }

    def "argument removed and added on renamed interface field"() {
        given:
        def oldSdl = '''
        type Query {
            issues: IssueQuery
        }
        interface IssueQuery {
            issues(id: [ID!]): [Issue]
        }
        type Issue {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            issues: IssueQuery
        }
        interface IssueQuery {
            issuesById(ids: [ID!]!): [Issue]
        }
        type Issue {
            id: ID!
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.interfaceDifferences["IssueQuery"] instanceof InterfaceModification
        def issueQueryChanges = changes.interfaceDifferences["IssueQuery"] as InterfaceModification
        issueQueryChanges.details.size() == 3

        def rename = issueQueryChanges.getDetails(InterfaceFieldRename)
        rename.size() == 1
        rename[0].oldName == "issues"
        rename[0].newName == "issuesById"

        def argumentRename = issueQueryChanges.getDetails(InterfaceFieldArgumentRename)
        argumentRename.size() == 1
        argumentRename[0].fieldName == "issuesById"
        argumentRename[0].oldName == "id"
        argumentRename[0].newName == "ids"

        def argumentTypeModification = issueQueryChanges.getDetails(InterfaceFieldArgumentTypeModification)
        argumentTypeModification.size() == 1
        argumentTypeModification[0].fieldName == "issuesById"
        argumentTypeModification[0].argumentName == "ids"
        argumentTypeModification[0].oldType == "[ID!]"
        argumentTypeModification[0].newType == "[ID!]!"
    }

    def "argument removed and added on renamed directive"() {
        given:
        def oldSdl = '''
        directive @dog(name: String) on FIELD_DEFINITION
        type Query {
            pet: String @dog
        }
        '''
        def newSdl = '''
        directive @cat(names: [String]) on FIELD_DEFINITION
        type Query {
            pet: String @cat
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.directiveDifferences["cat"] instanceof DirectiveModification
        def catChanges = changes.directiveDifferences["cat"] as DirectiveModification
        catChanges.oldName == "dog"
        catChanges.newName == "cat"
        catChanges.isNameChanged()
        catChanges.details.size() == 2

        def argumentRename = catChanges.getDetails(DirectiveArgumentRename)
        argumentRename.size() == 1
        argumentRename[0].oldName == "name"
        argumentRename[0].newName == "names"

        def argumentTypeModification = catChanges.getDetails(DirectiveArgumentTypeModification)
        argumentTypeModification.size() == 1
        argumentTypeModification[0].argumentName == "names"
        argumentTypeModification[0].oldType == "String"
        argumentTypeModification[0].newType == "[String]"
    }


    def "object field argument type and default value changed"() {
        given:
        def oldSdl = '''
        type Query {
            echo(message: String! = "Hello World"): String
        }
        '''
        def newSdl = '''
        type Query {
            echo(message: ID! = "1"): String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def queryChanges = changes.objectDifferences["Query"] as ObjectModification
        queryChanges.details.size() == 2

        def argumentTypeModification = queryChanges.getDetails(ObjectFieldArgumentTypeModification)
        argumentTypeModification.size() == 1
        argumentTypeModification[0].fieldName == "echo"
        argumentTypeModification[0].argumentName == "message"
        argumentTypeModification[0].oldType == "String!"
        argumentTypeModification[0].newType == "ID!"

        def defaultValueModification = queryChanges.getDetails(ObjectFieldArgumentDefaultValueModification)
        defaultValueModification.size() == 1
        defaultValueModification[0].fieldName == "echo"
        defaultValueModification[0].argumentName == "message"
        defaultValueModification[0].oldValue == '"Hello World"'
        defaultValueModification[0].newValue == '"1"'
    }

    def "interface field argument type and default value changed"() {
        given:
        def oldSdl = '''
        type Query {
            echo: EchoProvider
        }
        interface EchoProvider {
            send(message: String! = "Hello World"): String
        }
        '''
        def newSdl = '''
        type Query {
            echo: EchoProvider
        }
        interface EchoProvider {
            send(message: ID! = "1"): String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.interfaceDifferences["EchoProvider"] instanceof InterfaceModification
        def echoProviderChanges = changes.interfaceDifferences["EchoProvider"] as InterfaceModification
        echoProviderChanges.details.size() == 2

        def argumentTypeModification = echoProviderChanges.getDetails(InterfaceFieldArgumentTypeModification)
        argumentTypeModification.size() == 1
        argumentTypeModification[0].fieldName == "send"
        argumentTypeModification[0].argumentName == "message"
        argumentTypeModification[0].oldType == "String!"
        argumentTypeModification[0].newType == "ID!"

        def defaultValueModification = echoProviderChanges.getDetails(InterfaceFieldArgumentDefaultValueModification)
        defaultValueModification.size() == 1
        defaultValueModification[0].fieldName == "send"
        defaultValueModification[0].argumentName == "message"
        defaultValueModification[0].oldValue == '"Hello World"'
        defaultValueModification[0].newValue == '"1"'
    }

    def "directive argument type and default value changed"() {
        given:
        def oldSdl = '''
        directive @deleteBy(date: String = "+1 week") on FIELD_DEFINITION
        type Query {
            echo: String @deleteBy
        }
        '''
        def newSdl = '''
        directive @deleteBy(date: Int = 1000) on FIELD_DEFINITION
        type Query {
            echo: String @deleteBy
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.directiveDifferences["deleteBy"] instanceof DirectiveModification
        def deleteByChanges = changes.directiveDifferences["deleteBy"] as DirectiveModification
        deleteByChanges.details.size() == 2

        def argumentTypeModification = deleteByChanges.getDetails(DirectiveArgumentTypeModification)
        argumentTypeModification.size() == 1
        argumentTypeModification[0].argumentName == "date"
        argumentTypeModification[0].oldType == "String"
        argumentTypeModification[0].newType == "Int"

        def defaultValueModification = deleteByChanges.getDetails(DirectiveArgumentDefaultValueModification)
        defaultValueModification.size() == 1
        defaultValueModification[0].argumentName == "date"
        defaultValueModification[0].oldValue == '"+1 week"'
        defaultValueModification[0].newValue == '1000'
    }

    def "object field with argument removed and similarly named argument added"() {
        given:
        def oldSdl = """
        type Query {
            issues: IssueQuery
        }
        type IssueQuery {
            issues(id: [ID!]): [Issue]
            issuesById: [Issue]
        }
        type Issue {
            id: ID!
        }
        """
        def newSdl = '''
        type Query {
            issues: IssueQuery
        }
        type IssueQuery {
            issuesById(ids: [ID!]!): [Issue]
        }
        type Issue {
            id: ID!
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["IssueQuery"] instanceof ObjectModification
        def issueQueryChanges = changes.objectDifferences["IssueQuery"] as ObjectModification
        issueQueryChanges.details.size() == 2

        def fieldDeletion = issueQueryChanges.getDetails(ObjectFieldDeletion)
        fieldDeletion.size() == 1
        fieldDeletion[0].name == "issues"

        def fieldArgumentAddition = issueQueryChanges.getDetails(ObjectFieldArgumentAddition)
        fieldArgumentAddition.size() == 1
        fieldArgumentAddition[0].fieldName == "issuesById"
        fieldArgumentAddition[0].name == "ids"
    }

    def "interface field with argument removed and similarly named argument added"() {
        given:
        def oldSdl = """
        type Query {
            issues: IssueQuery
        }
        interface IssueQuery {
            issues(id: [ID!]): [Issue]
            issuesById: [Issue]
        }
        type Issue {
            id: ID!
        }
        """
        def newSdl = '''
        type Query {
            issues: IssueQuery
        }
        interface IssueQuery {
            issuesById(ids: [ID!]!): [Issue]
        }
        type Issue {
            id: ID!
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.interfaceDifferences["IssueQuery"] instanceof InterfaceModification
        def issueQueryChanges = changes.interfaceDifferences["IssueQuery"] as InterfaceModification
        issueQueryChanges.details.size() == 2

        def fieldDeletion = issueQueryChanges.getDetails(InterfaceFieldDeletion)
        fieldDeletion.size() == 1
        fieldDeletion[0].name == "issues"

        def fieldArgumentAddition = issueQueryChanges.getDetails(InterfaceFieldArgumentAddition)
        fieldArgumentAddition.size() == 1
        fieldArgumentAddition[0].fieldName == "issuesById"
        fieldArgumentAddition[0].name == "ids"
    }

    def "directive removed and similarly named argument added"() {
        given:
        def oldSdl = '''
        directive @dog(name: String) on FIELD_DEFINITION
        directive @cat on FIELD_DEFINITION
        type Query {
            pet: String
        }
        '''
        def newSdl = '''
        directive @cat(names: String) on FIELD_DEFINITION
        type Query {
            pet: String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.directiveDifferences["dog"] instanceof DirectiveDeletion
        def dogChanges = changes.directiveDifferences["dog"] as DirectiveDeletion
        dogChanges.name == "dog"

        changes.directiveDifferences["cat"] instanceof DirectiveModification
        def catChanges = changes.directiveDifferences["cat"] as DirectiveModification
        !catChanges.isNameChanged()
        catChanges.oldName == catChanges.newName
        catChanges.newName == "cat"
        catChanges.details.size() == 1

        def argumentAddition = catChanges.getDetails(DirectiveArgumentAddition)
        argumentAddition.size() == 1
        argumentAddition[0].name == "names"
    }

    def "change object description"() {
        given:
        def oldSdl = '''
        "HELLO"
        type Query {
            pet: String
        }
        '''
        def newSdl = '''
        type Query {
            pet: String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences.isEmpty()
    }

    def "change object field argument description"() {
        given:
        def oldSdl = '''
        type Query {
            pet(
                age: Int
            ): String
        }
        '''
        def newSdl = '''
        type Query {
            pet(
                "The age of the pet"
                age: Int
            ): String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences.isEmpty()
    }

    def "change interface description"() {
        given:
        def oldSdl = '''
        type Query {
            pet: Pet
        }
        interface Pet {
            name: String
        }
        '''
        def newSdl = '''
        type Query {
            pet: Pet
        }
        "Hello World"
        interface Pet {
            name: String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.interfaceDifferences.isEmpty()
    }

    def "change union description"() {
        given:
        def oldSdl = '''
        type Query {
            pet: Pet
        }
        union Pet = Dog | Cat
        type Dog {
            name: String
        }
        type Cat {
            name: String
        }
        '''
        def newSdl = '''
        type Query {
            pet: Pet
        }
        "----------------"
        union Pet = Dog | Cat
        type Dog {
            name: String
        }
        type Cat {
            name: String
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.unionDifferences.isEmpty()
    }

    def "change input object and field description"() {
        given:
        def oldSdl = '''
        type Query {
            pets(filter: PetFilter): [ID]
        }
        "Pet"
        input PetFilter {
            age: Int
        }
        '''
        def newSdl = '''
        type Query {
            pets(filter: PetFilter): [ID]
        }
        "Only pets matching the filter will be returned"
        input PetFilter {
            "The age in years"
            age: Int
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.inputObjectDifferences.isEmpty()
    }

    def "change enum type and value description"() {
        given:
        def oldSdl = '''
        type Query {
            pet(kind: PetKind): ID
        }
        enum PetKind {
            "doggo"
            DOG,
            CAT,
        }
        '''
        def newSdl = '''
        type Query {
            pet(kind: PetKind): ID
        }
        "The kind of pet"
        enum PetKind {
            DOG,
            CAT,
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.enumDifferences.isEmpty()
    }

    def "change scalar description"() {
        given:
        def oldSdl = '''
        scalar Age
        type Query {
            pet(age: Age): ID
        }
        '''
        def newSdl = '''
        "Represents age in years"
        scalar Age
        type Query {
            pet(age: Age): ID
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.scalarDifferences.isEmpty()
    }

    def "change directive description"() {
        given:
        def oldSdl = '''
        directive @cat on FIELD_DEFINITION
        type Query {
            pet: String @cat
        }
        '''
        def newSdl = '''
        "A cat or something"
        directive @cat on FIELD_DEFINITION
        type Query {
            pet: String @cat
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.directiveDifferences.isEmpty()
    }

    def "traversal order puts field changes before arguments"() {
        def objectOld = new Vertex(SchemaGraph.OBJECT, "target-1")
        objectOld.add("name", "Obey")
        def objectNew = new Vertex(SchemaGraph.OBJECT, "target-1")
        objectNew.add("name", "Ob")
        def changeObjectVertex = EditOperation.changeVertex(
                "Change object",
                objectOld,
                objectNew,
        )

        def newField = new Vertex(SchemaGraph.FIELD, "target-1")
        newField.add("name", "fried")
        def insertNewFieldVertex = EditOperation.insertVertex(
                "Insert new field",
                Vertex.newIsolatedNode("source-isolated-Field-1"),
                newField,
        )

        def newArgument = new Vertex(SchemaGraph.ARGUMENT, "target-1")
        newArgument.add("name", "alone")
        def insertNewArgumentVertex = EditOperation.insertVertex(
                "Insert argument",
                Vertex.newIsolatedNode("source-isolated-Argument-1"),
                newArgument,
        )

        def insertNewFieldEdge = EditOperation.insertEdge(
                "Insert Object -> Field Edge",
                new Edge(objectNew, newField),
        )

        def insertNewArgumentEdge = EditOperation.insertEdge(
                "Insert Field -> Argument Edge",
                new Edge(newField, newArgument),
        )

        when:
        def result = EditOperationAnalyzer.getTraversalOrder([
                insertNewArgumentVertex,
                insertNewFieldEdge,
                insertNewArgumentEdge,
                changeObjectVertex,
                insertNewFieldVertex,
        ])

        then:
        result == [
                changeObjectVertex,
                insertNewFieldVertex,
                insertNewArgumentVertex,
                insertNewFieldEdge,
                insertNewArgumentEdge,
        ]
    }

    def "less fields in the renamed object"() {
        given:
        def oldSdl = '''
        type Query {
            user(id: ID!): User
        }
        type User {
            id: String
            name: String
            account: String
            email: Boolean
            age: Int
        }
        '''
        def newSdl = '''
        type Query {
            account(id: ID!): Account
        }
        type Account {
            id: String
            name: String
            yearsOld: Int
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["User"] instanceof ObjectModification
        def userModification = changes.objectDifferences["User"] as ObjectModification
        userModification.isNameChanged()
        userModification.oldName == "User"
        userModification.newName == "Account"

        def deletions = userModification.getDetails(ObjectFieldDeletion)
        deletions.size() == 2
        deletions.collect { it.name }.toSet() == ["account", "email"] as Set

        def rename = userModification.getDetails(ObjectFieldRename)
        rename.size() == 1
        rename[0].oldName == "age"
        rename[0].newName == "yearsOld"
    }

    def "two possible mappings for object rename where one has less fields"() {
        given:
        def oldSdl = '''
        type Query {
            user(id: ID!): User
        }
        type User {
            id: String
            name: String
            account: String
            email: String
            age: Int
        }
        '''
        def newSdl = '''
        type Query {
            account(id: ID!): Account
        }
        type Account {
            yearsOld: Int
        }
        type Profile {
            id: String
            name: String
            account: String
            email: String
            age: Int
        }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["Account"] instanceof ObjectAddition

        changes.objectDifferences["User"] instanceof ObjectModification
        def userModification = changes.objectDifferences["User"] as ObjectModification
        userModification.isNameChanged()
        userModification.oldName == "User"
        userModification.newName == "Profile"

        userModification.details.isEmpty()
    }

    def "deleted field with fixed parent binding can map to isolated node"() {
        given:
        def oldSdl = '''
            type Query {
                notifications: NotificationQuery
            }
            type NotificationQuery {
                notificationFeed(
                    feedFilter: NotificationFeedFilter
                    first: Int = 25
                    after: String
                ): NotificationGroupedConnection!
                unseenNotificationCount(workspaceId: String, product: String): Int!
            }
            input NotificationFeedFilter {
                workspaceId: String
                productFilter: String
                groupId: String
            }
            type NotificationItem {
                notificationId: ID!
                workspaceId: String
            }
            type NotificationGroupedItem {
                groupId: ID!
                groupSize: Int!
                headNotification: NotificationItem!
                childItems(first: Int, after: String): [NotificationItem!]
            }
            type NotificationGroupedConnection {
                nodes: [NotificationGroupedItem!]!
            }
        '''
        def newSdl = '''
            type Query {
                notifications: NotificationQuery
            }
            type NotificationQuery {
                notificationFeed(
                    filter: NotificationFilter
                    first: Int = 25
                    after: String
                ): NotificationFeedConnection!
                notificationGroup(
                    groupId: String!
                    filter: NotificationFilter
                    first: Int = 25
                    after: String
                ): NotificationGroupConnection!
                unseenNotificationCount(workspaceId: String, product: String): Int!
            }
            input NotificationFilter {
                workspaceId: String
                productFilter: String
            }
            type NotificationEntityModel{
                objectId: String!
                containerId: String
                workspaceId: String
                cloudId: String
            }
            type NotificationItem {
                notificationId: ID!
                entityModel: NotificationEntityModel
                workspaceId: String
            }
            type NotificationHeadItem {
                groupId: ID!
                groupSize: Int!
                readStates: [String]!
                additionalTypes: [String!]!
                headNotification: NotificationItem!
                endCursor: String
            }
            type NotificationFeedConnection {
                nodes: [NotificationHeadItem!]!
            }
            type NotificationGroupConnection {
                nodes: [NotificationItem!]!
            }
        '''

        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["NotificationGroupedItem"] === changes.objectDifferences["NotificationHeadItem"]
        changes.objectDifferences["NotificationGroupedConnection"] === changes.objectDifferences["NotificationFeedConnection"]
        changes.objectDifferences["NotificationGroupedItem"] instanceof ObjectModification
        changes.objectDifferences["NotificationGroupedConnection"] instanceof ObjectModification
        changes.objectDifferences["NotificationEntityModel"] instanceof ObjectAddition
        changes.objectDifferences["NotificationGroupConnection"] instanceof ObjectAddition
        changes.objectDifferences["NotificationItem"] instanceof ObjectModification
        changes.objectDifferences["NotificationQuery"] instanceof ObjectModification

        changes.inputObjectDifferences["NotificationFeedFilter"] === changes.inputObjectDifferences["NotificationFilter"]
        changes.inputObjectDifferences["NotificationFeedFilter"] instanceof InputObjectModification

        def notificationFeedFilterChange = changes.inputObjectDifferences["NotificationFeedFilter"] as InputObjectModification
        notificationFeedFilterChange.details.size() == 1
        notificationFeedFilterChange.details[0] instanceof InputObjectFieldDeletion
        def groupIdInputObjectFieldDeletion = notificationFeedFilterChange.details[0] as InputObjectFieldDeletion
        groupIdInputObjectFieldDeletion.name == "groupId"
    }

    EditOperationAnalysisResult calcDiff(
            String oldSdl,
            String newSdl
    ) {
        def oldSchema = TestUtil.schema(oldSdl)
        def newSchema = TestUtil.schema(newSdl)
        def changes = new SchemaDiffing().diffAndAnalyze(oldSchema, newSchema)
        return changes
    }
}
