package graphql.schema.visibility

import graphql.StarWarsSchema
import graphql.schema.GraphQLInputObjectType
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLList.list

class BlockedFieldsTest extends Specification {

    static accessInputType = GraphQLInputObjectType.newInputObject()
            .name("Access")
            .field(newInputObjectField()
            .name("openRoles")
            .type(list(GraphQLString))
    )
            .field(newInputObjectField()
            .name("questionableRoles")
            .type(list(GraphQLString))
    )
            .field(newInputObjectField()
            .name("secretRoles")
            .type(list(GraphQLString))
    )
            .build()

    @Unroll
    def "basic blocking '#why'"() {

        given:
        def blockedFields = BlockedFields.newBlock().addPatterns(patterns).build()

        when:
        def fields = blockedFields.getFieldDefinitions(type).stream().map({ fd -> fd.getName() }).collect(Collectors.toList())

        then:
        fields == expectedFieldList

        where:
        why                              | type                              | patterns                                       | expectedFieldList
        "partial field name match"       | StarWarsSchema.characterInterface | [".*\\.name"]                                  | ["id", "friends", "appearsIn"]
        "no match"                       | StarWarsSchema.characterInterface | ["Character.mismatched"]                       | ["id", "name", "friends", "appearsIn"]
        "all blocked"                    | StarWarsSchema.characterInterface | [".*"]                                         | []
        "needs FQN to match"             | StarWarsSchema.characterInterface | ["name"]                                       | ["id", "name", "friends", "appearsIn"]
        "FQN"                            | StarWarsSchema.characterInterface | ["Character.name"]                             | ["id", "friends", "appearsIn"]
        "multiple patterns"              | StarWarsSchema.characterInterface | ["Character.name", ".*.id"]                    | ["friends", "appearsIn"]

        "input partial field name match" | accessInputType                   | [".*\\.secretRoles"]                           | ["openRoles", "questionableRoles"]
        "input no match"                 | accessInputType                   | ["Access.mismatched"]                          | ["openRoles", "questionableRoles", "secretRoles"]
        "input all blocked"              | accessInputType                   | [".*"]                                         | []
        "input needs FQN to match"       | accessInputType                   | ["secretRoles"]                                | ["openRoles", "questionableRoles", "secretRoles"]
        "input FQN"                      | accessInputType                   | ["Access.secretRoles"]                         | ["openRoles", "questionableRoles"]
        "input multiple patterns"        | accessInputType                   | ["Access.secretRoles", ".*.questionableRoles"] | ["openRoles"]

    }
}
