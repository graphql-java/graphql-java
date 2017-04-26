package graphql.language

import graphql.parser.Parser
import spock.lang.Specification

class AstPrinterTest extends Specification {

    Document parse(String input) {
        new Parser().parseDocument(input)
    }

    String printAst(String input) {
        Document document = parse(input)

        new AstPrinter().printAst(document)
    }

    String printAst(Node node) {
        new AstPrinter().printAst(node)
    }

    def starWarsSchema = """
# objects can have comments
# over a number of lines
schema {
    query: QueryType
}

type QueryType {
    # the hero of the film
    hero(episode: Episode): Character
    human(id : String) : Human
    droid(id: ID!): Droid
}

enum Episode {
    NEWHOPE
    EMPIRE
    JEDI
}

interface Character {
    id: ID!
    name: String!
    friends: [Character]
    appearsIn: [Episode]!
}

type Human implements Character {
    id: ID!
    name: String!
    friends: [Character]
    appearsIn: [Episode]!
    homePlanet: String
}

type Droid implements Character {
    id: ID!
    name: String!
    friends: [Character]
    appearsIn: [Episode]!
    primaryFunction: String
}

"""

    //-------------------------------------------------
    def "test schema printing a complete schema"() {
        String output = printAst(starWarsSchema)

        expect:
        //
        // notice how it tightens everything up
        //
        output == """# objects can have comments
# over a number of lines
schema {
  query: QueryType
}

type QueryType {
  # the hero of the film
  hero(episode: Episode): Character
  human(id: String): Human
  droid(id: ID!): Droid
}

enum Episode {
  NEWHOPE
  EMPIRE
  JEDI
}

interface Character {
  id: ID!
  name: String!
  friends: [Character]
  appearsIn: [Episode]!
}

type Human implements Character {
  id: ID!
  name: String!
  friends: [Character]
  appearsIn: [Episode]!
  homePlanet: String
}

type Droid implements Character {
  id: ID!
  name: String!
  friends: [Character]
  appearsIn: [Episode]!
  primaryFunction: String
}
"""
    }

    //-------------------------------------------------
    def "test schema printing specific schema node"() {
        def document = parse(starWarsSchema)
        String output = printAst(document.getDefinitions().get(0))

        expect:
        output == """# objects can have comments
# over a number of lines
schema {
  query: QueryType
}"""
    }

    def "test schema printing specific type node"() {
        def document = parse(starWarsSchema)
        String output = printAst(document.getDefinitions().get(1))

        expect:
        output == """type QueryType {
  # the hero of the film
  hero(episode: Episode): Character
  human(id: String): Human
  droid(id: ID!): Droid
}"""
    }

    //-------------------------------------------------
    def "ast printing of queries"() {
        def query = """
{
  empireHero: hero(episode: EMPIRE) {
    name
  }
  jediHero: hero(episode: JEDI) {
    name
  }
}"""
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == """query {
  empireHero: hero(episode: EMPIRE) {
    name
  }
  jediHero: hero(episode: JEDI) {
    name
  }
}
"""

    }

    //-------------------------------------------------
    def "ast printing of fragments"() {
        def query = """
{
  leftComparison: hero(episode: EMPIRE) {
    ...comparisonFields
  }
  rightComparison: hero(episode: JEDI) {
    ...comparisonFields
  }
}

fragment comparisonFields on Character {
  name
  appearsIn
  friends {
    name
  }
}
"""
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == """query {
  leftComparison: hero(episode: EMPIRE) {
    ...comparisonFields
  }
  rightComparison: hero(episode: JEDI) {
    ...comparisonFields
  }
}

fragment comparisonFields on Character {
  name
  appearsIn
  friends {
    name
  }
}
"""
    }
}
