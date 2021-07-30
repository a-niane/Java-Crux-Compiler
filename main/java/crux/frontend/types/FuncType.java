package crux.frontend.types;

/**
 * FuncType for functions args is a TypeList to create a type for each param and push it to the list
 * ret is the type of the function return type, could be int,bool,void Two functions are equivalent
 * if their args and ret are also equivalent This Class should implement Call method
 */
public final class FuncType extends Type {
  private TypeList args;
  private Type ret;

  public FuncType(TypeList args, Type returnType) {
    this.args = args;
    this.ret = returnType;
  }

  public Type getRet() {
    return ret;
  }

  public TypeList getArgs() {
    return args;
  }

  //call is the only one that is overwritten (make sure order of parameters matter)
  //(int x, bool y, int z) != (false, true, 10)
  //verify if the args are equivalent, return function funcType
  @Override
  Type call(Type args)
  {
    if(!this.getArgs().equivalent(args)) //if argument structures are not equal (two typelists can compare themselves)
    {
      return super.call(args);
    }
    return this.getRet(); //returns result of function call
  }

  @Override
  public boolean equivalent(Type that)
  {
    if (that.getClass() != FuncType.class)
    {
      return false;
    }

    //also compare contents of the function
    if(this.getRet() == ((FuncType) that).getRet() && this.getArgs() == ((FuncType) that).getArgs())
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
  Type index(Type that)
  {
    return super.index(that);
  }

  @Override
  Type assign(Type source)
  {
    return super.assign(source);
  }
   */

  @Override
  public String toString() {
    return "func(" + args + "):" + ret;
  }
}
