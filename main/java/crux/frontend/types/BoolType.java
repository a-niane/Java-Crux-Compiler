package crux.frontend.types;

/**
 * Types for Booleans values This should implement the equivalent methods along with and,or, and not
 * equivalent will check if the param is instance of BoolType
 */
public final class BoolType extends Type
{
  @Override
  Type and(Type that)
  {
    if (!this.equivalent(that)) {
      return super.and(that);
    }
    return new BoolType();
  }

  @Override
  Type or(Type that)
  {
    if (!this.equivalent(that)) {
      return super.or(that);
    }
    return new BoolType();
  }

  @Override
  Type not()
  {
    return new BoolType(); //this is a boolType, so it will always work
  }

  //cannot use > or >=, but can use == or !=
  @Override
  Type compare(Type that)
  {
    if (!this.equivalent(that)) {
      return super.compare(that);
    }
    return new BoolType();
  }

  @Override //such as bool ten = false;
  Type assign(Type source)
  {
    if (!this.equivalent(source)) {
      return super.assign(source);
    }
    return new VoidType(); //assignments don't return
  }

  /*
  @Override
  Type add(Type that)
  {
    return super.add(that);
  }

  @Override
  Type sub(Type that)
  {
    return super.sub(that);
  }

  @Override
  Type mul(Type that)
  {
    return super.mul(that);
  }

  @Override
  Type div(Type that)
  {
    return super.div(that);
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

  //already implemented
  @Override
  public boolean equivalent(Type that) {
    return that.getClass() == BoolType.class;
  }

  @Override
  public String toString() {
    return "bool";
  }
}
