package graphql.schema.idl;

import org.testng.annotations.Test;
import graphql.GraphQLException;

public class SchemaParserTest {

    @Test
    public void testNumberFormatException() {
      String[] malformedStrings = {
        "{B(t:66E3333333320,t:#\n66666666660)},622»» »»»6666662}}6666660t:z6666"
      };

      for (String malformed : malformedStrings) {
        try {
          SchemaParser parser = new SchemaParser();
          parser.parse(malformed);
        } catch (GraphQLException e) {
          // Known exception
        }
      }
    }
}
