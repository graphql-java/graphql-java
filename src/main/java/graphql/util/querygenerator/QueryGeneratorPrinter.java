package graphql.util.querygenerator;

import java.util.List;
import java.util.stream.Collectors;

public class QueryGeneratorPrinter {
    private final String indentationString;
    private final int indentationSpaces;
    private final int startingIndentationLevel;

    public QueryGeneratorPrinter(String indentationString, int indentationSpaces, int startingIndentationLevel) {
        this.indentationString = indentationString;
        this.indentationSpaces = indentationSpaces;
        this.startingIndentationLevel = startingIndentationLevel;
    }

    public String print(List<QueryGenerator.FieldData> fields) {
        String initialIndentation = startingIndentationLevel == 0 ? ""
                : indentationString.repeat(this.startingIndentationLevel * this.indentationSpaces);

        return fields.stream()
                .map(field -> printField(field, startingIndentationLevel + 1))
                .collect(Collectors.joining("", initialIndentation + "{\n", initialIndentation + "}\n"));
    }

    private String printField(QueryGenerator.FieldData fieldData, int level) {
        String indentation = indentationString.repeat(level * this.indentationSpaces);
        StringBuilder sb = new StringBuilder();
        sb.append(indentation).append(fieldData.name);
        if (fieldData.fields != null && !fieldData.fields.isEmpty()) {
            sb.append(" {\n");
            for (QueryGenerator.FieldData subField : fieldData.fields) {
                sb.append(printField(subField, level + 1));
            }
            sb.append(indentation).append("}\n");
        } else {
            sb.append("\n");
        }
        return sb.toString();
    }
}
