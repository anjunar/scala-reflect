package reflect

trait ConstructorInvoker[T] {
  def newInstance(args: Any*): T
  def parameterCount: Int
  def isVarArgs: Boolean
}

object ConstructorInvoker {
  
  def apply[T](factory: Seq[Any] => T, params: Int, varArgs: Boolean = false): ConstructorInvoker[T] =
    new ConstructorInvoker[T] {
      override def newInstance(args: Any*): T = factory(args.toSeq)
      override def parameterCount: Int = params
      override def isVarArgs: Boolean = varArgs
    }
}
