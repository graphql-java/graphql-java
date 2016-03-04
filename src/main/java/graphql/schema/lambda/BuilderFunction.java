package graphql.schema.lambda;

/**
 * Created by cliu on 3/3/16.
 */
public interface BuilderFunction<T> {

  public T implement(T builder);

}
