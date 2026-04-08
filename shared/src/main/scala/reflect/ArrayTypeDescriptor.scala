package reflect

final case class ArrayTypeDescriptor(
  componentType: TypeDescriptor
) extends TypeDescriptor {
  
  override def typeName: String = s"${componentType.typeName}[]"
  override def simpleName: String = componentType.typeName.split('.').last + "[]"
  override def annotations: Array[Annotation] = Array.empty
  override def properties: Array[PropertyDescriptor] = Array.empty
  override def constructors: Array[ConstructorDescriptor] = Array.empty
  override def baseTypes: Array[String] = Array("scala.Array")
  override def typeParameters: Array[TypeDescriptor] = Array.empty
  override def isAbstract: Boolean = false
  override def isFinal: Boolean = true
  override def isCaseClass: Boolean = false
}
