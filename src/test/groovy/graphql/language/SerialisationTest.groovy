package graphql.language

import graphql.GraphQLError
import graphql.InvalidSyntaxError
import graphql.TestUtil
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class SerialisationTest extends Specification {

    static <T> T serialisedDownAndBack(T inputObj) {
        def file = File.createTempFile("serialised", ".bin")

        FileOutputStream f = new FileOutputStream(file)
        ObjectOutputStream o = new ObjectOutputStream(f)

        o.writeObject(inputObj)
        o.close()

        FileInputStream fi = new FileInputStream(file)
        ObjectInputStream oi = new ObjectInputStream(fi)

        T object = oi.readObject() as T
        oi.close()

        return object
    }

    static SourceLocation srcLoc(int line, int col) {
        new SourceLocation(line, col)
    }

    def query = '''
                #comment
                query HeroForEpisode($ep: Episode!) {
                  hero(episode: $ep) {
                    name
                    ... on Droid {
                      primaryFunction
                    }
                    ... on Human {
                      height
                    }
                  }
                  
                  rightComparison: hero(episode: JEDI) {
                     ...comparisonFields
                  }
                  
                  differentArgObjects( s : "s", int : 1, b : true, list : [{a : "s"}, 1, "s"], obj : {x : "s", y : true} )
                  
                  directiveOnField @skip(if:true)
                }
                
                fragment comparisonFields on Character {
                  name
                  appearsIn
                  friends {
                    name
                  }
                }
        '''

    def "#1071 basic document serialisation"() {

        // this helps prove that we can serialise a query document
        // and hence allow caching of that parsed document via
        // systems that require serializable objects

        when:
        Document originalDoc = TestUtil.parseQuery(query)
        def originalAst = AstPrinter.printAst(originalDoc)

        Document newDocument = serialisedDownAndBack(originalDoc)
        def newAst = AstPrinter.printAst(newDocument)

        then:

        originalAst == newAst
    }

    def "basic error serialisation"() {
        when:
        GraphQLError syntaxError = new InvalidSyntaxError(srcLoc(1, 2), "Bad Syntax")

        GraphQLError serialisedError = serialisedDownAndBack(syntaxError)

        then:

        syntaxError.getMessage() == serialisedError.getMessage()
        syntaxError.getLocations() == serialisedError.getLocations()
    }

    def "PreparsedDocumentEntry with document is serializable"() {

        when:
        Document originalDoc = TestUtil.parseQuery(query)
        def originalEntry = new PreparsedDocumentEntry(originalDoc)
        def originalAst = AstPrinter.printAst(originalEntry.getDocument())

        PreparsedDocumentEntry newEntry = serialisedDownAndBack(originalEntry)
        def newAst = AstPrinter.printAst(newEntry.getDocument())

        then:

        originalAst == newAst
    }

    def "PreparsedDocumentEntry with errors is serializable"() {

        when:
        GraphQLError syntaxError1 = new InvalidSyntaxError(srcLoc(1, 1), "Bad Syntax 1")
        GraphQLError validationError2 = new ValidationError(ValidationErrorType.FieldUndefined, srcLoc(2, 2), "Bad Query 2")
        def originalEntry = new PreparsedDocumentEntry([syntaxError1, validationError2])

        PreparsedDocumentEntry newEntry = serialisedDownAndBack(originalEntry)

        then:

        newEntry.getErrors().size() == 2
        newEntry.getErrors().get(0).getMessage() == syntaxError1.getMessage()
        newEntry.getErrors().get(0).getLocations() == syntaxError1.getLocations()

        newEntry.getErrors().get(1).getMessage() == validationError2.getMessage()
        newEntry.getErrors().get(1).getLocations() == validationError2.getLocations()
    }
}
