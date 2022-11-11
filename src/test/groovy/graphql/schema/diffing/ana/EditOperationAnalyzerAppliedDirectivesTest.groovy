package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Ignore
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaDifference.*

class EditOperationAnalyzerAppliedDirectivesTest extends Specification{

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

    @Ignore
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
