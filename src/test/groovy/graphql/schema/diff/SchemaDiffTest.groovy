package graphql.schema.diff

import graphql.TestUtil
import graphql.language.Argument
import graphql.language.Directive
import graphql.language.IntValue
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.StringValue
import graphql.language.Type
import graphql.language.TypeDefinition
import graphql.language.TypeKind
import graphql.language.TypeName
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.GraphQLScalarType
import graphql.schema.PropertyDataFetcher
import graphql.schema.TypeResolver
import graphql.schema.diff.reporting.CapturingReporter
import graphql.schema.diff.reporting.ChainedReporter
import graphql.schema.diff.reporting.PrintStreamReporter
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.UnionWiringEnvironment
import graphql.schema.idl.WiringFactory
import spock.lang.Specification

import java.util.stream.Collectors

class SchemaDiffTest extends Specification {
    private CapturingReporter reporter
    private ChainedReporter chainedReporter

    private static final TypeResolver NULL_TYPE_RESOLVER = { env -> null }

    static GraphQLScalarType CUSTOM_SCALAR = new GraphQLScalarType("CustomScalar", "CustomScalar", new Coercing() {
        @Override
        Object serialize(Object dataFetcherResult) {
            throw new UnsupportedOperationException("Not implemented")
        }

        @Override
        Object parseValue(Object input) {
            throw new UnsupportedOperationException("Not implemented")
        }

        @Override
        Object parseLiteral(Object input) {
            throw new UnsupportedOperationException("Not implemented")
        }
    })

    static RuntimeWiring wireWithNoFetching() {

        return RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new WiringFactory() {

            @Override
            boolean providesTypeResolver(UnionWiringEnvironment environment) {
                return true
            }

            @Override
            boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
                return true
            }

            @Override
            TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
                return NULL_TYPE_RESOLVER
            }

            @Override
            TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
                return NULL_TYPE_RESOLVER
            }

            @Override
            DataFetcher getDefaultDataFetcher(FieldWiringEnvironment environment) {
                return new PropertyDataFetcher(environment.getFieldDefinition().getName())
            }
        })
                .scalar(CUSTOM_SCALAR)
                .build()
    }

    void setup() {
        reporter = new CapturingReporter()
        chainedReporter = new ChainedReporter(reporter, new PrintStreamReporter())
    }

    DiffSet diffSet(String newFile) {
        def schemaOld = TestUtil.schemaFile("diff/" + "schema_ABaseLine.graphqls", wireWithNoFetching())
        def schemaNew = TestUtil.schemaFile("diff/" + newFile, wireWithNoFetching())

        def diffSet = DiffSet.diffSet(schemaOld, schemaNew)
        diffSet
    }


    def "change_in_null_ness"() {

        given:
        Type baseLine = new NonNullType(new ListType(new TypeName("foo")))
        Type same = new NonNullType(new ListType(new TypeName("foo")))

        Type less = new ListType(new TypeName("foo"))


        def diff = new SchemaDiff()

        def sameType = diff.checkTypeWithNonNullAndList(baseLine, same)

        def lessStrict = diff.checkTypeWithNonNullAndList(baseLine, less)

        // not allowed as old clients wont work
        def moreStrict = diff.checkTypeWithNonNullAndList(less, baseLine)


        expect:
        sameType == null
        lessStrict == null
        moreStrict == DiffCategory.STRICTER
    }

    def "change_in_list_ness"() {

        given:
        Type baseLine = new NonNullType(new ListType(new TypeName("foo")))
        Type notList = new NonNullType(new TypeName("foo"))

        def diff = new SchemaDiff()

        def noLongerList = diff.checkTypeWithNonNullAndList(baseLine, notList)

        expect:
        noLongerList == DiffCategory.INVALID
    }

    DiffEvent lastBreakage(CapturingReporter capturingReporter) {
        def breakages = capturingReporter.getBreakages()
        breakages.size() == 0 ? null : breakages.get(breakages.size() - 1)
    }

    def "directives_controlled_via_options"() {

        given:
        DiffCtx ctx = new DiffCtx(reporter, null, null)

        TypeDefinition left = new ObjectTypeDefinition("fooType")

        def oneDirective = [new Directive("bar")]
        def twoDirectives = [new Directive("foo"), new Directive("bar")]

        def diff = new SchemaDiff()
        diff.checkDirectives(ctx, left, twoDirectives, oneDirective)
        def notChecked = lastBreakage(reporter)

        diff = new SchemaDiff(SchemaDiff.Options.defaultOptions().enforceDirectives())
        diff.checkDirectives(ctx, left, twoDirectives, oneDirective)
        def missingDirective = lastBreakage(reporter)

        expect:
        notChecked == null
        missingDirective.category == DiffCategory.MISSING
    }


    def "directives enforced to be the same"() {

        given:
        DiffCtx ctx = new DiffCtx(reporter, null, null)

        TypeDefinition left = new ObjectTypeDefinition("fooType")


        def oneDirective = [new Directive("bar")]
        def twoDirectives = [new Directive("foo"), new Directive("bar")]

        def diff = new SchemaDiff(SchemaDiff.Options.defaultOptions().enforceDirectives())

        diff.checkDirectives(ctx, left, twoDirectives, oneDirective)
        def missingDirective = lastBreakage(reporter)

        def oldDirective = new Directive("foo", [
                new Argument("arg1", new StringValue("p1")),
                new Argument("arg2", new StringValue("p1")),
        ])

        def newDirective = new Directive("foo", [
                new Argument("arg1", new StringValue("p1")),
                new Argument("arg3", new StringValue("p1")),
        ])

        diff.checkDirectives(ctx, left, [oldDirective], [newDirective])
        def missingArgs = lastBreakage(reporter)


        def newDirectiveDiffDefaultType = new Directive("foo", [
                new Argument("arg1", new StringValue("p1")),
                new Argument("arg2", new IntValue(new BigInteger("123"))),
        ])

        diff.checkDirectives(ctx, left, [oldDirective], [newDirectiveDiffDefaultType])
        def changedType = lastBreakage(reporter)

        expect:
        missingDirective.category == DiffCategory.MISSING
        missingArgs.category == DiffCategory.MISSING
        changedType.category == DiffCategory.INVALID
        reporter.getBreakageCount() == 3
    }

    def "same schema diff"() {
        DiffSet diffSet = diffSet("schema_ABaseLine.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 0
    }

    def "additional field"() {
        DiffSet diffSet = diffSet("schema_with_additional_field.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 0

        List<DiffEvent> newFieldEvents = reporter.infos.stream()
                .filter{de -> de.typeName == "Ainur" && de.fieldName == "surname"}
                .collect(Collectors.toList())

        newFieldEvents.size() == 2

        newFieldEvents[0].level == DiffLevel.INFO
        newFieldEvents[1].level == DiffLevel.INFO
        newFieldEvents[1].category == DiffCategory.ADDITION
    }

    def "missing fields on interface"() {
        DiffSet diffSet = diffSet("schema_interface_fields_missing.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 8 // 2 fields removed from interface, affecting 3 types
        reporter.breakages[0].category == DiffCategory.MISSING
        reporter.breakages[0].typeKind == TypeKind.Interface

        reporter.breakages[1].category == DiffCategory.MISSING
        reporter.breakages[1].typeKind == TypeKind.Interface
    }

    def "missing members on union"() {
        DiffSet diffSet = diffSet("schema_missing_union_members.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 1 // 1 member removed
        reporter.breakages[0].category == DiffCategory.MISSING
        reporter.breakages[0].typeKind == TypeKind.Union

    }

    def "missing fields on object"() {
        DiffSet diffSet = diffSet("schema_missing_object_fields.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 2 // 2 fields removed
        reporter.breakages[0].category == DiffCategory.MISSING
        reporter.breakages[0].typeKind == TypeKind.Object
        reporter.breakages[0].fieldName == 'colour'

        reporter.breakages[1].category == DiffCategory.MISSING
        reporter.breakages[1].typeKind == TypeKind.Object
        reporter.breakages[1].fieldName == 'temperament'

    }

    def "missing operation"() {
        DiffSet diffSet = diffSet("schema_missing_operation.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 1
        reporter.breakages[0].category == DiffCategory.MISSING
        reporter.breakages[0].typeKind == TypeKind.Operation
        reporter.breakages[0].typeName == 'Mutation'

    }

    def "missing input object fields"() {
        DiffSet diffSet = diffSet("schema_missing_input_object_fields.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 1
        reporter.breakages[0].category == DiffCategory.MISSING
        reporter.breakages[0].typeKind == TypeKind.InputObject
        reporter.breakages[0].fieldName == 'queryTarget'

    }

    def "changed input object field types"() {
        DiffSet diffSet = diffSet("schema_changed_input_object_fields.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 2
        reporter.breakages[0].category == DiffCategory.INVALID
        reporter.breakages[0].typeName == 'Questor'
        reporter.breakages[0].typeKind == TypeKind.InputObject
        reporter.breakages[0].fieldName == 'queryTarget'

        reporter.breakages[1].category == DiffCategory.STRICTER
        reporter.breakages[1].typeName == 'Questor'
        reporter.breakages[1].typeKind == TypeKind.InputObject
        reporter.breakages[1].fieldName == 'newMandatoryField'

    }

    def "changed type kind"() {
        DiffSet diffSet = diffSet("schema_changed_type_kind.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 1
        reporter.breakages[0].category == DiffCategory.INVALID
        reporter.breakages[0].typeName == 'Character'
        reporter.breakages[0].typeKind == TypeKind.Union

    }

    def "missing object field args"() {
        DiffSet diffSet = diffSet("schema_missing_field_arguments.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 2

        reporter.breakages[0].category == DiffCategory.MISSING
        reporter.breakages[0].typeKind == TypeKind.Object
        reporter.breakages[0].typeName == "Mutation"
        reporter.breakages[0].fieldName == 'being'
        reporter.breakages[0].components.contains("questor")

        reporter.breakages[1].category == DiffCategory.MISSING
        reporter.breakages[1].typeKind == TypeKind.Object
        reporter.breakages[0].typeName == "Mutation"
        reporter.breakages[1].fieldName == 'sword'

    }

    def "missing enum value"() {
        DiffSet diffSet = diffSet("schema_missing_enum_value.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 1
        reporter.breakages[0].category == DiffCategory.MISSING
        reporter.breakages[0].typeKind == TypeKind.Enum
        reporter.breakages[0].typeName == 'Temperament'
        reporter.breakages[0].components.contains("Duplicitous")

    }

    def "changed object field args"() {
        DiffSet diffSet = diffSet("schema_changed_field_arguments.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 2
        reporter.breakages[0].category == DiffCategory.INVALID
        reporter.breakages[0].typeKind == TypeKind.Object
        reporter.breakages[0].fieldName == 'sword'

        reporter.breakages[1].category == DiffCategory.INVALID
        reporter.breakages[1].typeKind == TypeKind.Object
        reporter.breakages[1].fieldName == 'sword'

    }

    def "changed type on object"() {
        DiffSet diffSet = diffSet("schema_changed_object_fields.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 4
        reporter.breakages[0].category == DiffCategory.STRICTER
        reporter.breakages[0].typeName == 'Query'
        reporter.breakages[0].typeKind == TypeKind.Object
        reporter.breakages[0].fieldName == 'being'

        reporter.breakages[1].category == DiffCategory.INVALID
        reporter.breakages[1].typeName == 'Query'
        reporter.breakages[1].typeKind == TypeKind.Object
        reporter.breakages[1].fieldName == 'beings'

        reporter.breakages[2].category == DiffCategory.STRICTER
        reporter.breakages[2].typeName == 'Query'
        reporter.breakages[2].typeKind == TypeKind.Object
        reporter.breakages[2].fieldName == 'customScalar'

        reporter.breakages[3].category == DiffCategory.STRICTER
        reporter.breakages[3].typeName == 'Query'
        reporter.breakages[3].typeKind == TypeKind.Object
        reporter.breakages[3].fieldName == 'wizards'

    }

    def "dangerous changes "() {
        DiffSet diffSet = diffSet("schema_dangerous_changes.graphqls")

        def diff = new SchemaDiff()
        diff.diffSchema(diffSet, chainedReporter)

        expect:
        reporter.breakageCount == 0
        reporter.dangerCount == 3

        reporter.dangers[0].category == DiffCategory.ADDITION
        reporter.dangers[0].typeName == "Character"
        reporter.dangers[0].typeKind == TypeKind.Union
        reporter.dangers[0].components.contains("BenignFigure")

        reporter.dangers[1].category == DiffCategory.DIFFERENT
        reporter.dangers[1].typeName == "Query"
        reporter.dangers[1].typeKind == TypeKind.Object
        reporter.dangers[1].fieldName == "being"
        reporter.dangers[1].components.contains("type")

        reporter.dangers[2].category == DiffCategory.ADDITION
        reporter.dangers[2].typeName == "Temperament"
        reporter.dangers[2].typeKind == TypeKind.Enum
        reporter.dangers[2].components.contains("Nonplussed")

    }

}
