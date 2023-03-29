package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
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
        type Foo implements Node2 & NewI{
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
