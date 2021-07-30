package crux.frontend.types;

/**
 * Types for Integers values This should implement the equivalent methods along with add,sub, mul,
 * div, and compare equivalent will check if the param is instance of IntType
 */
public final class IntType extends Type
{
  @Override
  Type add(Type that)
  {
    if (!this.equivalent(that)) {
      return super.add(that);
    }
    return new IntType();
  }

  @Override
  Type sub(Type that)
  {
    if (!this.equivalent(that)) {
      return super.sub(that);
    }
    return new IntType();
  }

  @Override
  Type mul(Type that)
  {
    if (!this.equivalent(that)) {
      return super.mul(that);
    }
    return new IntType();
  }

  @Override
  Type div(Type that)
  {
    if (!this.equivalent(that)) {
      return super.div(that);
    }
    return new IntType();
  }

  //'>=' | '<=' | '!=' | '==' | '<' | '>'
  @Override
  Type compare(Type that)
  {
    if (!this.equivalent(that)) {
      return super.div(that);
    }
    return new BoolType(); //returning boolean is fine
  }

  //like "int four = 4;"
  @Override
  Type assign(Type source)
  {
    if (!this.equivalent(source)) {
      return super.assign(source);
    }
    return new VoidType(); //assignments don't return
  }

  /* None of these need to be overwritten
  @Override
  Type and(Type that)
  {
    return super.and(that);
  }

  @Override
  Type or(Type that)
  {
    return super.or(that);
  }

  @Override
  Type not()
  {
    return super.not();
  }

  @Override
  Type deref()
  {
    return super.deref();
  }

  @Override
  Type index(Type that)
  {
    return super.index(that);
  }

  @Override
  Type call(Type args)
  {
    return super.call(args);
  }
  */

  @Override
  public boolean equivalent(Type that)
  {
    return that.getClass() == IntType.class;
  }

  @Override
  public String toString() {
    return "int";
  }
}
