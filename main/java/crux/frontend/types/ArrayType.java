package crux.frontend.types;

/**
 * This Type will Array data type base is the base of the array, could be int, bool, or char for
 * cruxlang This should implement the equivalent methods Two arrays are equivalent if their bases
 * are equivalent and have same extend
 */
public final class ArrayType extends Type {
  private final Type base;
  private final long extent;

  public ArrayType(long extent, Type base) {
    this.extent = extent;
    this.base = base;
  }

  public Type getBase() {
    return base;
  }

  public long getExtent() {
    return extent;
  }

  //I'm guessing these functions are supposed to be overwritten here?
  @Override
  Type index(Type that)
  {
    if (that.getClass() != IntType.class) //only numbers index
    {
      return super.index(that);
    }
    return this.getBase(); //array of booleans should return boolean
  }

  @Override //like int args[] = {1.2.3}?
  Type assign(Type source)
  {
    if (!this.equivalent(source))
    {
      return super.assign(source);
    }
    return new VoidType(); //assignment has no return
  }

  @Override
  public boolean equivalent(Type that)
  {
    if (that.getClass() != ArrayType.class)
    {
      return false;
    }

    //check the contents of both arrays
    if (this.getBase().equivalent(((ArrayType) that).getBase()) && (this.getExtent() == ((ArrayType) that).getExtent()))
    {
      return true;
    }
    return false;
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
  Type compare(Type that)
  {
    return super.compare(that);
  }

  @Override
  Type deref()
  {
    return super.deref();
  }

  @Override
  Type call(Type args)
  {
    return super.call(args);
  }

   */

  @Override
  public String toString() {
    return String.format("array[%d,%s]", extent, base);
  }
}
