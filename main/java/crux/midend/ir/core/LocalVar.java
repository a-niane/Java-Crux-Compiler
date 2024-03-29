package crux.midend.ir.core;

import crux.frontend.types.Type;

/**
 * A scalar variable is a variable that holds a scalar value - in our language that is a variable
 * containing either an integer or a boolean.
 */
public final class LocalVar extends Variable {
  /**
   * Do not directly create a LocalVar. Instead use the getTempVar method in the Function class.
   */

  public LocalVar(Type type) {
    super(type);
  }

  /**
   * Do not directly create a LocalVar. Instead use the getTempVar method in the Function class.
   */

  public LocalVar(Type type, String name) {
    super(type, name);
    mName = String.format("$%s", mName);
  }

  public String toString() {
    return mName;
  }
}
