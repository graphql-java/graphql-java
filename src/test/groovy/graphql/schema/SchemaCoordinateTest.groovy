package graphql.schema

import graphql.AssertException
import spock.lang.Specification

class SchemaCoordinateTest extends Specification {

    // -- factory methods --

    def "namedType creates correct coordinate"() {
        when:
        def coord = SchemaCoordinate.namedType("User")

        then:
        coord.getType() == SchemaCoordinateType.NAMED_TYPE
        coord.getTypeName() == "User"
        coord.getMemberName() == null
        coord.getArgumentName() == null
        coord.getDirectiveName() == "User"
    }

    def "field creates correct coordinate"() {
        when:
        def coord = SchemaCoordinate.field("User", "name")

        then:
        coord.getType() == SchemaCoordinateType.FIELD
        coord.getTypeName() == "User"
        coord.getMemberName() == "name"
        coord.getArgumentName() == null
    }

    def "inputField creates correct coordinate"() {
        when:
        def coord = SchemaCoordinate.inputField("SearchInput", "query")

        then:
        coord.getType() == SchemaCoordinateType.INPUT_FIELD
        coord.getTypeName() == "SearchInput"
        coord.getMemberName() == "query"
        coord.getArgumentName() == null
    }

    def "enumValue creates correct coordinate"() {
        when:
        def coord = SchemaCoordinate.enumValue("Status", "ACTIVE")

        then:
        coord.getType() == SchemaCoordinateType.ENUM_VALUE
        coord.getTypeName() == "Status"
        coord.getMemberName() == "ACTIVE"
        coord.getArgumentName() == null
    }

    def "fieldArgument creates correct coordinate"() {
        when:
        def coord = SchemaCoordinate.fieldArgument("Query", "searchBusiness", "criteria")

        then:
        coord.getType() == SchemaCoordinateType.FIELD_ARGUMENT
        coord.getTypeName() == "Query"
        coord.getMemberName() == "searchBusiness"
        coord.getArgumentName() == "criteria"
    }

    def "directive creates correct coordinate"() {
        when:
        def coord = SchemaCoordinate.directive("deprecated")

        then:
        coord.getType() == SchemaCoordinateType.DIRECTIVE
        coord.getDirectiveName() == "deprecated"
        coord.getTypeName() == "deprecated"
        coord.getMemberName() == null
        coord.getArgumentName() == null
    }

    def "directiveArgument creates correct coordinate"() {
        when:
        def coord = SchemaCoordinate.directiveArgument("deprecated", "reason")

        then:
        coord.getType() == SchemaCoordinateType.DIRECTIVE_ARGUMENT
        coord.getDirectiveName() == "deprecated"
        coord.getMemberName() == null
        coord.getArgumentName() == "reason"
    }

    // -- toString --

    def "toString produces spec-standard format for named type"() {
        expect:
        SchemaCoordinate.namedType("User").toString() == "User"
    }

    def "toString produces spec-standard format for field"() {
        expect:
        SchemaCoordinate.field("User", "name").toString() == "User.name"
    }

    def "toString produces spec-standard format for input field"() {
        expect:
        SchemaCoordinate.inputField("SearchInput", "query").toString() == "SearchInput.query"
    }

    def "toString produces spec-standard format for enum value"() {
        expect:
        SchemaCoordinate.enumValue("Status", "ACTIVE").toString() == "Status.ACTIVE"
    }

    def "toString produces spec-standard format for field argument"() {
        expect:
        SchemaCoordinate.fieldArgument("Query", "searchBusiness", "criteria").toString() == "Query.searchBusiness(criteria:)"
    }

    def "toString produces spec-standard format for directive"() {
        expect:
        SchemaCoordinate.directive("deprecated").toString() == "@deprecated"
    }

    def "toString produces spec-standard format for directive argument"() {
        expect:
        SchemaCoordinate.directiveArgument("deprecated", "reason").toString() == "@deprecated(reason:)"
    }

    // -- parse --

    def "parse named type"() {
        when:
        def coord = SchemaCoordinate.parse("User")

        then:
        coord.getType() == SchemaCoordinateType.NAMED_TYPE
        coord.getTypeName() == "User"
    }

    def "parse field coordinate"() {
        when:
        def coord = SchemaCoordinate.parse("User.name")

        then:
        coord.getType() == SchemaCoordinateType.FIELD
        coord.getTypeName() == "User"
        coord.getMemberName() == "name"
    }

    def "parse field argument coordinate"() {
        when:
        def coord = SchemaCoordinate.parse("Query.searchBusiness(criteria:)")

        then:
        coord.getType() == SchemaCoordinateType.FIELD_ARGUMENT
        coord.getTypeName() == "Query"
        coord.getMemberName() == "searchBusiness"
        coord.getArgumentName() == "criteria"
    }

    def "parse directive coordinate"() {
        when:
        def coord = SchemaCoordinate.parse("@deprecated")

        then:
        coord.getType() == SchemaCoordinateType.DIRECTIVE
        coord.getDirectiveName() == "deprecated"
    }

    def "parse directive argument coordinate"() {
        when:
        def coord = SchemaCoordinate.parse("@deprecated(reason:)")

        then:
        coord.getType() == SchemaCoordinateType.DIRECTIVE_ARGUMENT
        coord.getDirectiveName() == "deprecated"
        coord.getArgumentName() == "reason"
    }

    def "parse null throws AssertException"() {
        when:
        SchemaCoordinate.parse(null)

        then:
        thrown(AssertException)
    }

    def "parse empty string throws AssertException"() {
        when:
        SchemaCoordinate.parse("")

        then:
        thrown(AssertException)
    }

    def "parse invalid directive argument format throws AssertException"() {
        when:
        SchemaCoordinate.parse("@deprecated(reason)")

        then:
        thrown(AssertException)
    }

    def "parse invalid field argument format throws AssertException"() {
        when:
        SchemaCoordinate.parse("Query.search(term)")

        then:
        thrown(AssertException)
    }

    // -- round-trip parse/toString --

    def "round-trip parse and toString for all formats"() {
        expect:
        SchemaCoordinate.parse(input).toString() == input

        where:
        input << [
                "User",
                "User.name",
                "Query.searchBusiness(criteria:)",
                "@deprecated",
                "@deprecated(reason:)"
        ]
    }

    // -- equals and hashCode --

    def "equals for same coordinate"() {
        when:
        def coord1 = SchemaCoordinate.field("User", "name")
        def coord2 = SchemaCoordinate.field("User", "name")

        then:
        coord1 == coord2
        coord1.hashCode() == coord2.hashCode()
    }

    def "not equals for different type"() {
        when:
        def coord1 = SchemaCoordinate.field("User", "name")
        def coord2 = SchemaCoordinate.inputField("User", "name")

        then:
        coord1 != coord2
    }

    def "not equals for different names"() {
        when:
        def coord1 = SchemaCoordinate.field("User", "name")
        def coord2 = SchemaCoordinate.field("User", "email")

        then:
        coord1 != coord2
    }

    def "not equals to null"() {
        expect:
        SchemaCoordinate.namedType("User") != null
    }

    def "equals to self"() {
        when:
        def coord = SchemaCoordinate.namedType("User")

        then:
        coord == coord
    }

    // -- builder --

    def "builder creates coordinate"() {
        when:
        def coord = SchemaCoordinate.newSchemaCoordinate()
                .type(SchemaCoordinateType.FIELD)
                .typeName("User")
                .memberName("name")
                .build()

        then:
        coord.getType() == SchemaCoordinateType.FIELD
        coord.getTypeName() == "User"
        coord.getMemberName() == "name"
        coord.toString() == "User.name"
    }

    def "transform creates modified copy"() {
        given:
        def original = SchemaCoordinate.field("User", "name")

        when:
        def modified = original.transform({ builder -> builder.memberName("email") })

        then:
        modified.getType() == SchemaCoordinateType.FIELD
        modified.getTypeName() == "User"
        modified.getMemberName() == "email"
        modified.toString() == "User.email"
        original.getMemberName() == "name"
    }

    // -- null checks on factory methods --

    def "namedType with null typeName throws"() {
        when:
        SchemaCoordinate.namedType(null)

        then:
        thrown(AssertException)
    }

    def "field with null typeName throws"() {
        when:
        SchemaCoordinate.field(null, "name")

        then:
        thrown(AssertException)
    }

    def "field with null fieldName throws"() {
        when:
        SchemaCoordinate.field("User", null)

        then:
        thrown(AssertException)
    }

    def "fieldArgument with null argumentName throws"() {
        when:
        SchemaCoordinate.fieldArgument("Query", "search", null)

        then:
        thrown(AssertException)
    }

    def "directive with null directiveName throws"() {
        when:
        SchemaCoordinate.directive(null)

        then:
        thrown(AssertException)
    }

    def "directiveArgument with null argumentName throws"() {
        when:
        SchemaCoordinate.directiveArgument("deprecated", null)

        then:
        thrown(AssertException)
    }
}
