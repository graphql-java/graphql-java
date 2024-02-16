package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveAddition
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentAddition
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentDeletion
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentRename
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentValueModification
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveDeletion
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveDirectiveArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveEnumLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveEnumValueLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInputObjectFieldLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInputObjectLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceFieldArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceFieldLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectFieldArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectFieldLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveScalarLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveUnionLocation
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveModification
import static graphql.schema.diffing.ana.SchemaDifference.EnumModification
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectModification
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceModification
import static graphql.schema.diffing.ana.SchemaDifference.ObjectModification
import static graphql.schema.diffing.ana.SchemaDifference.ScalarModification
import static graphql.schema.diffing.ana.SchemaDifference.UnionModification

class EditOperationAnalyzerAppliedDirectivesTest extends Specification {

    def "applied directive argument added interface field"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d(arg1: "foo")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def argumentAddition = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentAddition)
        def location = argumentAddition[0].locationDetail as AppliedDirectiveInterfaceFieldLocation
        location.interfaceName == "I"
        location.fieldName == "foo"
        argumentAddition[0].argumentName == "arg1"
    }

    def "applied directive argument value changed object"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on OBJECT
        
        type Query @d(arg1:"foo") {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg1: String)  on OBJECT
        
        type Query @d(arg1: "bar") {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def detail = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveObjectLocation
        location.name == "Query"
        location.directiveName == "d"
        detail[0].argumentName == "arg1"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed object field"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "bar")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def detail = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "foo"
        location.directiveName == "d"
        detail[0].argumentName == "arg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed object field argument"() {
        given:
        def oldSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d(directiveArg: "foo")) : String 
        }
        '''
        def newSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d(directiveArg: "bar")) : String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def detail = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentValueModification)
        def locationDetail = detail[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation
        locationDetail.objectName == "Query"
        locationDetail.fieldName == "foo"
        locationDetail.argumentName == "arg"
        locationDetail.directiveName == "d"
        detail[0].argumentName == "directiveArg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }


    def "applied directive argument value changed interface"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I @d(arg1: "foo") {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I @d(arg1: "bar") {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def detail = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveInterfaceLocation
        location.name == "I"
        location.directiveName == "d"
        detail[0].argumentName == "arg1"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }


    def "applied directive argument value changed interface field"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d(arg1: "bar")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def detail = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveInterfaceFieldLocation
        location.interfaceName == "I"
        location.fieldName == "foo"
        location.directiveName == "d"
        detail[0].argumentName == "arg1"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed interface field argument"() {
        given:
        def oldSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d(directiveArg: "foo") ): String
        }
        '''
        def newSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d(directiveArg: "bar") ): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def detail = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation
        location.interfaceName == "I"
        location.fieldName == "foo"
        location.argumentName == "arg"
        location.directiveName == "d"
        detail[0].argumentName == "directiveArg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed input object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "foo"){
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "bar") {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def detail = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveInputObjectLocation
        location.name == "I"
        location.directiveName == "d"
        detail[0].argumentName == "arg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'

    }


    def "applied directive argument value changed input object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "foo")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "bar")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def detail = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveInputObjectFieldLocation
        location.inputObjectName == "I"
        location.fieldName == "a"
        location.directiveName == "d"
        detail[0].argumentName == "arg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed enum"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg:"foo") { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg: "bar") { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def detail = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveEnumLocation
        location.name == "E"
        location.directiveName == "d"
        detail[0].argumentName == "arg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed enum value"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E { A, B @d(arg: "foo") }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E  { A, B @d(arg: "bar") }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def detail = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveEnumValueLocation
        location.enumName == "E"
        location.valueName == "B"
        location.directiveName == "d"
        detail[0].argumentName == "arg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed union"() {
        given:
        def oldSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U @d(arg: "foo") = A | B
        type A { a: String }
        type B { b: String }
        '''
        def newSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U @d(arg: "bar") = A | B
        type A { a: String }
        type B { b: String }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def detail = (changes.unionDifferences["U"] as UnionModification).getDetails(AppliedDirectiveArgumentValueModification)
        (detail[0].locationDetail as AppliedDirectiveUnionLocation).name == "U"
        detail[0].argumentName == "arg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed scalar"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime @d(arg: "foo")
        type Query {
            foo: DateTime 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime @d(arg: "bar")
        type Query {
            foo: DateTime 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["DateTime"] instanceof ScalarModification
        def detail = (changes.scalarDifferences["DateTime"] as ScalarModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveScalarLocation
        location.name == "DateTime"
        location.directiveName == "d"
        detail[0].argumentName == "arg"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }

    def "applied directive argument value changed directive argument"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String @d(arg1:"foo")) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String @d(arg1:"bar")) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d2"] instanceof DirectiveModification
        (changes.directiveDifferences["d2"] as DirectiveModification).details.size() == 1
        def detail = (changes.directiveDifferences["d2"] as DirectiveModification).getDetails(AppliedDirectiveArgumentValueModification)
        def location = detail[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation
        location.directiveDefinitionName == "d2"
        location.directiveName == "d"
        location.argumentName == "arg"
        detail[0].argumentName == "arg1"
        detail[0].oldValue == '"foo"'
        detail[0].newValue == '"bar"'
    }


    def "applied directive argument added interface"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I @d {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I @d(arg1: "foo") {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def argumentAddition = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentAddition)
        def location = argumentAddition[0].locationDetail as AppliedDirectiveInterfaceLocation
        location.name == "I"
        argumentAddition[0].argumentName == "arg1"
    }

    def "applied directive argument deleted interface field "() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def argumentDeletions = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = argumentDeletions[0].locationDetail as AppliedDirectiveInterfaceFieldLocation
        location.interfaceName == "I"
        location.fieldName == "foo"
        argumentDeletions[0].argumentName == "arg1"
    }

    def "applied directive argument deleted interface"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I @d(arg1: "foo") {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on INTERFACE
        
        type Query implements I {
            foo: String 
        }
        interface I @d{
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def argumentDeletions = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = argumentDeletions[0].locationDetail as AppliedDirectiveInterfaceLocation
        location.name == "I"
        argumentDeletions[0].argumentName == "arg1"
    }


    def "applied directive added input object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "foo")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectFieldLocation).inputObjectName == "I"
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectFieldLocation).fieldName == "a"
        appliedDirective[0].name == "d"
    }

    def "applied directive added object field"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldLocation).fieldName == "foo"
        appliedDirective[0].name == "d"
    }

    def "applied directive argument value changed object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo1")
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo2")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentValueModifications = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentValueModification)
        (argumentValueModifications[0].locationDetail as AppliedDirectiveObjectFieldLocation).objectName == "Query"
        (argumentValueModifications[0].locationDetail as AppliedDirectiveObjectFieldLocation).fieldName == "foo"
        argumentValueModifications[0].argumentName == "arg"
        argumentValueModifications[0].oldValue == '"foo1"'
        argumentValueModifications[0].newValue == '"foo2"'
    }

    def "applied directive argument name changed object field"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String, arg2: String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1: String, arg2: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg2: "foo")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentRenames = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentRename)
        def location = argumentRenames[0].locationDetail as AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "foo"
        argumentRenames[0].oldName == "arg1"
        argumentRenames[0].newName == "arg2"
    }

    def "applied directive argument added object"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on OBJECT
        
        type Query @d {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg1: String)  on OBJECT
        
        type Query @d(arg1: "foo") {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentAddition = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentAddition)
        def location = argumentAddition[0].locationDetail as AppliedDirectiveObjectLocation
        location.name == "Query"
        argumentAddition[0].argumentName == "arg1"
    }

    def "applied directive argument added object field"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d
        }
        '''
        def newSdl = '''
        directive @d(arg1: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg1: "foo")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentAddition = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentAddition)
        def location = argumentAddition[0].locationDetail as AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "foo"
        argumentAddition[0].argumentName == "arg1"
    }

    def "applied directive argument deleted object field"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentDeletions = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = argumentDeletions[0].locationDetail as AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "foo"
        argumentDeletions[0].argumentName == "arg1"
    }

    def "applied directive argument deleted object"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on OBJECT
        
        type Query @d(arg1: "foo"){
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg1: String) on OBJECT
        
        type Query @d {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentDeletions = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = argumentDeletions[0].locationDetail as AppliedDirectiveObjectLocation
        location.name == "Query"
        argumentDeletions[0].argumentName == "arg1"
    }

    def "applied directive added input object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "foo") {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectLocation).name == "I"
        appliedDirective[0].name == "d"
    }

    def "applied directive added object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on OBJECT
        
        type Query {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on OBJECT
        
        type Query @d(arg: "foo") {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        (changes.objectDifferences["Query"] as ObjectModification).details.size() == 1
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectLocation).name == "Query"
        appliedDirective[0].name == "d"
    }

    def "applied directive added interface"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg: String) on INTERFACE
        
        type Query implements I {
            foo: String 
        }
        interface I @d(arg: "foo") {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceLocation).name == "I"
        appliedDirective[0].name == "d"
    }

    def "applied directive added union"() {
        given:
        def oldSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U = A | B
        type A { a: String }
        type B { b: String }
        '''
        def newSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U @d(arg: "foo") = A | B
        type A { a: String }
        type B { b: String }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def appliedDirective = (changes.unionDifferences["U"] as UnionModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveUnionLocation).name == "U"
        appliedDirective[0].name == "d"
    }

    def "applied directive added scalar"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  
        type Query {
            foo: DateTime 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  @d(arg: "foo")
        type Query {
            foo: DateTime 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["DateTime"] instanceof ScalarModification
        def appliedDirective = (changes.scalarDifferences["DateTime"] as ScalarModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveScalarLocation).name == "DateTime"
        appliedDirective[0].name == "d"
    }

    def "applied directive added enum"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg: "foo") { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def appliedDirective = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumLocation).name == "E"
        appliedDirective[0].name == "d"
    }

    def "applied directive added enum value"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E  { A, B @d(arg: "foo") }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def appliedDirective = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumValueLocation).enumName == "E"
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumValueLocation).valueName == "B"
        appliedDirective[0].name == "d"
    }

    def "applied directive argument added enum value"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E { A, B @d }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E  { A, B @d(arg: "foo") }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def argumentAdded = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveArgumentAddition)
        def location = argumentAdded[0].locationDetail as AppliedDirectiveEnumValueLocation
        location.enumName == "E"
        location.valueName == "B"
        location.directiveName == "d"
        argumentAdded[0].argumentName == "arg"
    }

    def "applied directive argument deleted enum value"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E { A, B @d(arg: "foo") }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E  { A, B @d }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def argumentDeletion = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = argumentDeletion[0].locationDetail as AppliedDirectiveEnumValueLocation
        location.enumName == "E"
        location.valueName == "B"
        location.directiveName == "d"
        argumentDeletion[0].argumentName == "arg"
    }


    def "applied directive added object field argument"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String) : String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d(arg: "foo")) : String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).fieldName == "foo"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }

    def "applied directive argument added object field argument"() {
        given:
        def oldSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d) : String 
        }
        '''
        def newSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d(directiveArg: "foo")) : String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirectiveArgumentAddition = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentAddition)
        def locationDetail = appliedDirectiveArgumentAddition[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation
        locationDetail.objectName == "Query"
        locationDetail.fieldName == "foo"
        locationDetail.argumentName == "arg"
        appliedDirectiveArgumentAddition[0].argumentName == "directiveArg"
    }

    def "applied directive argument deleted object field argument"() {
        given:
        def oldSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d(directiveArg: "foo")) : String 
        }
        '''
        def newSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d) : String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirectiveArgumentDeletion = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentDeletion)
        def locationDetail = appliedDirectiveArgumentDeletion[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation
        locationDetail.objectName == "Query"
        locationDetail.fieldName == "foo"
        locationDetail.argumentName == "arg"
        appliedDirectiveArgumentDeletion[0].argumentName == "directiveArg"
    }

    def "applied directive added interface field argument"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).interfaceName == "I"
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).fieldName == "foo"
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }

    def "applied directive argument added interface field argument"() {
        given:
        def oldSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d): String
        }
        '''
        def newSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d(directiveArg: "foo") ): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentAddition)
        def location = appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation
        location.interfaceName == "I"
        location.fieldName == "foo"
        location.argumentName == "arg"
        appliedDirective[0].argumentName == "directiveArg"
    }

    def "applied directive argument deleted interface field argument"() {
        given:
        def oldSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d(directiveArg: "foo")): String
        }
        '''
        def newSdl = '''
        directive @d(directiveArg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation
        location.interfaceName == "I"
        location.fieldName == "foo"
        location.argumentName == "arg"
        appliedDirective[0].argumentName == "directiveArg"
    }

    def "applied directive added directive argument "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String @d) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d2"] instanceof DirectiveModification
        def appliedDirective = (changes.directiveDifferences["d2"] as DirectiveModification).getDetails(AppliedDirectiveAddition)
        def location = appliedDirective[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation
        location.directiveDefinitionName == "d2"
        location.argumentName == "arg"
        location.directiveName == "d"
        appliedDirective[0].name == "d"
    }

    def "applied directive argument added directive argument "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg2:String @d) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg2:String @d(arg:"foo") ) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d2"] instanceof DirectiveModification
        (changes.directiveDifferences["d2"] as DirectiveModification).details.size() == 1
        def appliedDirectiveArgumentAddition = (changes.directiveDifferences["d2"] as DirectiveModification).getDetails(AppliedDirectiveArgumentAddition)
        def location = appliedDirectiveArgumentAddition[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation
        location.directiveName == "d"
        location.argumentName == "arg2"
        appliedDirectiveArgumentAddition[0].argumentName == "arg"
    }

    def "applied directive argument deleted directive argument "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg2:String @d(arg:"foo")) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg2:String @d ) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d2"] instanceof DirectiveModification
        (changes.directiveDifferences["d2"] as DirectiveModification).details.size() == 1
        def appliedDirectiveArgumentDeletion = (changes.directiveDifferences["d2"] as DirectiveModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = appliedDirectiveArgumentDeletion[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation
        location.directiveName == "d"
        location.argumentName == "arg2"
        appliedDirectiveArgumentDeletion[0].argumentName == "arg"
    }


    def "applied directive deleted object"() {
        given:
        def oldSdl = '''
        directive @d(arg: String)  on OBJECT
        
        type Query @d(arg: "foo") {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on OBJECT
        
        type Query {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectLocation).name == "Query"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted argument directive argument"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String @d(arg1:"foo")) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d2"] instanceof DirectiveModification
        // whole applied directive is deleted, so we don't count the applied argument deletion
        (changes.directiveDifferences["d2"] as DirectiveModification).details.size() == 1
        def appliedDirective = (changes.directiveDifferences["d2"] as DirectiveModification).getDetails(AppliedDirectiveDeletion)
        def location = appliedDirective[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation
        location.directiveDefinitionName == "d2"
        location.argumentName == "arg"
        location.directiveName == "d"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted enum"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg: "foo") { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def diff = changes.enumDifferences["E"] as EnumModification

        diff.getDetails().size() == 1

        def appliedDirective = diff.getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumLocation).name == "E"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted argument enum"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg: "foo") { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def argumentDeleted = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveArgumentDeletion)
        (argumentDeleted[0].locationDetail as AppliedDirectiveEnumLocation).name == "E"
        argumentDeleted[0].argumentName == "arg"
    }

    def "applied directive added argument enum"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg: "foo"){ A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def argumentAdded = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveArgumentAddition)
        (argumentAdded[0].locationDetail as AppliedDirectiveEnumLocation).name == "E"
        argumentAdded[0].argumentName == "arg"

    }


    def "applied directive deleted enum value"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E  { A, B @d(arg: "foo") }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def appliedDirective = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumValueLocation).enumName == "E"
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumValueLocation).valueName == "B"
        appliedDirective[0].name == "d"
    }


    def "applied directive deleted input object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "foo") {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectLocation).name == "I"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted input object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "foo")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectFieldLocation).inputObjectName == "I"
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectFieldLocation).fieldName == "a"
        appliedDirective[0].name == "d"
    }


    def "applied directive argument added input object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d
        }
        type Query {
            foo(arg: I): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "foo")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def argumentAdded = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveArgumentAddition)
        def location = argumentAdded[0].locationDetail as AppliedDirectiveInputObjectFieldLocation
        location.inputObjectName == "I"
        location.fieldName == "a"
        location.directiveName == "d"
        argumentAdded[0].argumentName == "arg"
    }

    def "applied directive argument deleted input object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "foo")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d
        }
        type Query {
            foo(arg: I): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def argumentDeletion = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = argumentDeletion[0].locationDetail as AppliedDirectiveInputObjectFieldLocation
        location.inputObjectName == "I"
        location.fieldName == "a"
        location.directiveName == "d"
        argumentDeletion[0].argumentName == "arg"
    }


    def "applied directive deleted interface"() {
        given:
        def oldSdl = '''
        directive @d(arg: String) on INTERFACE
        
        type Query implements I {
            foo: String 
        }
        interface I @d(arg: "foo") {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceLocation).name == "I"
        appliedDirective[0].name == "d"
    }


    def "applied directive deleted interface field argument"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).interfaceName == "I"
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).fieldName == "foo"
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted object field"() {
        given:
        def oldSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldLocation).fieldName == "foo"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted object field argument"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d(arg: "foo")) : String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String) : String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).fieldName == "foo"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }


    def "applied directive deleted scalar"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  @d(arg: "foo")
        type Query {
            foo: DateTime 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  
        type Query {
            foo: DateTime 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["DateTime"] instanceof ScalarModification
        def appliedDirective = (changes.scalarDifferences["DateTime"] as ScalarModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveScalarLocation).name == "DateTime"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted union"() {
        given:
        def oldSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U @d(arg: "foo") = A | B
        type A { a: String }
        type B { b: String }
        '''
        def newSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U = A | B
        type A { a: String }
        type B { b: String }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences.keySet() == ["U"] as Set
        changes.unionDifferences["U"] instanceof UnionModification
        def diff = changes.unionDifferences["U"] as UnionModification
        diff.details.size() == 1

        def appliedDirective = diff.getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveUnionLocation).name == "U"
        appliedDirective[0].name == "d"
    }

    def "applied directive argument added union"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on UNION
        type Query {
            foo: FooBar  
        }
        union FooBar @d =  A | B
        type A { a: String }
        type B { b: String }
        '''
        def newSdl = '''
        directive @d(arg:String) on UNION
        type Query {
            foo: FooBar  
        }
        union FooBar @d(arg:"arg") = A | B
        type A { a: String }
        type B { b: String }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["FooBar"] instanceof UnionModification
        def argumentAdded = (changes.unionDifferences["FooBar"] as UnionModification).getDetails(AppliedDirectiveArgumentAddition)
        (argumentAdded[0].locationDetail as AppliedDirectiveUnionLocation).name == "FooBar"
        (argumentAdded[0].locationDetail as AppliedDirectiveUnionLocation).directiveName == "d"
        argumentAdded[0].argumentName == "arg"
    }

    def "applied directive argument deleted union"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on UNION
        type Query {
            foo: FooBar  
        }
        union FooBar @d(arg:"arg") =  A | B
        type A { a: String }
        type B { b: String }
        '''
        def newSdl = '''
        directive @d(arg:String) on UNION
        type Query {
            foo: FooBar  
        }
        union FooBar @d = A | B
        type A { a: String }
        type B { b: String }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["FooBar"] instanceof UnionModification
        def argumentDeleted = (changes.unionDifferences["FooBar"] as UnionModification).getDetails(AppliedDirectiveArgumentDeletion)
        (argumentDeleted[0].locationDetail as AppliedDirectiveUnionLocation).name == "FooBar"
        (argumentDeleted[0].locationDetail as AppliedDirectiveUnionLocation).directiveName == "d"
        argumentDeleted[0].argumentName == "arg"
    }


    def "applied directive argument added scalar"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime @d 
        type Query {
            foo: DateTime 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  @d(arg: "foo")
        type Query {
            foo: DateTime 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["DateTime"] instanceof ScalarModification
        def argumentAdded = (changes.scalarDifferences["DateTime"] as ScalarModification).getDetails(AppliedDirectiveArgumentAddition)
        (argumentAdded[0].locationDetail as AppliedDirectiveScalarLocation).name == "DateTime"
        argumentAdded[0].argumentName == "arg"
    }

    def "applied directive argument deleted scalar"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime @d(arg: "foo")
        type Query {
            foo: DateTime 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  @d
        type Query {
            foo: DateTime 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["DateTime"] instanceof ScalarModification
        def argumentDeletion = (changes.scalarDifferences["DateTime"] as ScalarModification).getDetails(AppliedDirectiveArgumentDeletion)
        (argumentDeletion[0].locationDetail as AppliedDirectiveScalarLocation).name == "DateTime"
        argumentDeletion[0].argumentName == "arg"
    }

    def "applied directive argument added input object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "foo") {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def argumentAdded = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveArgumentAddition)
        (argumentAdded[0].locationDetail as AppliedDirectiveInputObjectLocation).name == "I"
        argumentAdded[0].argumentName == "arg"
    }

    def "applied directive argument deleted input object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "foo") {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d  {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def argumentAdded = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveArgumentDeletion)
        (argumentAdded[0].locationDetail as AppliedDirectiveInputObjectLocation).name == "I"
        argumentAdded[0].argumentName == "arg"
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
