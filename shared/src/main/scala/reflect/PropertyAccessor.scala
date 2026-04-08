package reflect

trait PropertyAccessor[E, V] {
  def get(instance: E): V
  def set(instance: E, value: V): Unit
  def hasSetter: Boolean
}

object PropertyAccessor {
  
  def apply[E, V](getter: E => V, setter: (E, V) => Unit): PropertyAccessor[E, V] =
    new PropertyAccessor[E, V] {
      override def get(instance: E): V = getter(instance)
      override def set(instance: E, value: V): Unit = setter(instance, value)
      override def hasSetter: Boolean = true
    }
  
  def readOnly[E, V](getter: E => V): PropertyAccessor[E, V] =
    new PropertyAccessor[E, V] {
      override def get(instance: E): V = getter(instance)
      override def set(instance: E, value: V): Unit =
        throw new UnsupportedOperationException(s"Property is read-only")
      override def hasSetter: Boolean = false
    }
}
