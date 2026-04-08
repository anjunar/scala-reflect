package reflect

final case class TypeVariableDescriptor(
  name: String,
  bounds: Array[String]
) extends TypeDescriptor {
  
  override def typeName: String = name
  override def simpleName: String = name
  override def annotations: Array[Annotation] = Array.empty
  override def properties: Array[PropertyDescriptor] = Array.empty
  override def constructors: Array[ConstructorDescriptor] = Array.empty
  override def baseTypes: Array[String] = bounds
  override def typeParameters: Array[TypeDescriptor] = Array.empty
  override def isAbstract: Boolean = false
  override def isFinal: Boolean = false
  override def isCaseClass: Boolean = false
}
