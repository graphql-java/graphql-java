package graphql.validation.rules;

import static graphql.validation.ValidationErrorType.UniqueObjectFieldName;

import com.google.common.collect.Sets;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.Set;

public class UniqueObjectFieldName extends AbstractRule {
  public UniqueObjectFieldName(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
    super(validationContext, validationErrorCollector);
  }

  @Override
  public void checkObjectValue(ObjectValue objectValue) {
    Set<String> fieldNames = Sets.newHashSetWithExpectedSize(objectValue.getObjectFields().size());

    for (ObjectField field : objectValue.getObjectFields()) {
      String fieldName = field.getName();

      if (fieldNames.contains(fieldName)) {
        String message = i18n(UniqueObjectFieldName, "UniqueObjectFieldName.duplicateFieldName", fieldName);
        addError(UniqueObjectFieldName, objectValue.getSourceLocation(), message);
      } else {
        fieldNames.add(fieldName);
      }
    }
  }
}
