package crux.frontend.types;

import java.util.stream.Stream;

/**
 * This Type will represent memory location base is the type of data that will be stored at that
 * memory locaiton This Type can have the following methods: deref,index, assign, and equivalent
 */
public final class AddressType extends Type
{
  private final Type base;

  public AddressType(Type base) {
    this.base = base;
  }

  public Type getBaseType()
  {
    return base;
  }

  //only deref, index, and assign are overwritten

  //deref: (check if deref) verify the base is one of the supported types (else, return super) -> cannot assign void
  //index: verify the base is an arraytype and return new AddressType (else, return super)

  //assign: (check if !deref) verify base and source then return voidtype for assignment (else, return super)
  @Override //njdudhu@3 = ??
  Type assign(Type source)
  {
    if (!this.equivalent(source)) {
      return super.assign(source);
    }
    return new VoidType(); //assignment has no return
  }

  @Override
  Type deref() //a[b[5]]
  {
    //no type checking here
    if(base.getClass() == IntType.class || base.getClass() == BoolType.class || base.getClass() == ArrayType.class)
    {
        return base;
    }
    return super.deref(); //cannot be void, func, etc.
  }

  @Override
  Type index(Type that)
  {
    // array = [ [1,2,3], [4,5,6] ]
    if (that.getClass() != ArrayType.class) //only numbers index
    {
      return super.index(that);
    }

    return new AddressType(this.getBaseType()); //array of booleans should return boolean
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
  Type call(Type args)
  {
    return super.call(args);
  }

   */


  //already implemented
  @Override
  public boolean equivalent(Type that) {
    if (that.getClass() != AddressType.class)
      return false;

    var aType = (AddressType) that;
    return base.equivalent(aType.base);
  }

  @Override
  public String toString() {
    return "Address(" + base + ")";
  }
}
