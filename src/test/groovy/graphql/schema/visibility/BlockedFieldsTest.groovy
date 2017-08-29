package graphql.schema.visibility

import graphql.StarWarsSchema
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

class BlockedFieldsTest extends Specification {

    @Unroll
    def "basic blocking '#why'"() {

        given:
        def blockedFields = BlockedFields.newBlock().addPatterns(patterns).build()

        when:
        def fields = blockedFields.getFieldDefinitions(type).stream().map({ fd -> fd.getName() }).collect(Collectors.toList())

        then:
        fields == expectedFieldList

        where:
        why                        | type                              | patterns                    | expectedFieldList
        "partial field name match" | StarWarsSchema.characterInterface | [".*\\.name"]               | ["id", "friends", "appearsIn"]
        "no match"                 | StarWarsSchema.characterInterface | ["Character.mismatched"]    | ["id", "name", "friends", "appearsIn"]
        "all blocked"              | StarWarsSchema.characterInterface | [".*"]                      | []
        "needs FQN to match"       | StarWarsSchema.characterInterface | ["name"]                    | ["id", "name", "friends", "appearsIn"]
        "FQN"                      | StarWarsSchema.characterInterface | ["Character.name"]          | ["id", "friends", "appearsIn"]
        "multiple patterns"        | StarWarsSchema.characterInterface | ["Character.name", ".*.id"] | ["friends", "appearsIn"]
    }
}
