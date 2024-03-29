package crux.frontend.ast;

import crux.frontend.ast.traversal.NodeVisitor;

import java.util.List;

/**
 * AST node for Return value
 */
public final class Return extends BaseNode implements Statement {
  private final Expression value;

  public Return(Position position, Expression value) {
    super(position);
    this.value = value;
  }

  public Expression getValue() {
    return value;
  }

  @Override
  public List<Node> getChildren() {
    return List.of(value);
  }

  @Override
  public <T> T accept(NodeVisitor<? extends T> visitor) {
    return visitor.visit(this);
  }
}
