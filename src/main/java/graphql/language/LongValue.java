package graphql.language;

import graphql.language.AbstractNode;
import graphql.language.Node;
import graphql.language.Value;

import java.util.ArrayList;
import java.util.List;

public class LongValue extends AbstractNode implements Value{


  private long value;

  public LongValue(long value) {
      this.value = value;
  }

  public long getValue() {
      return value;
  }

  public void setValue(long value) {
      this.value = value;
  }

  @Override
  public List<Node> getChildren() {
      return new ArrayList<>();
  }

  @Override
  public boolean isEqualTo(Node o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LongValue intValue = (LongValue) o;

      return value == intValue.value;

  }


  @Override
  public String toString() {
      return "LongValue{" +
              "value=" + value +
              '}';
  }
  
}
