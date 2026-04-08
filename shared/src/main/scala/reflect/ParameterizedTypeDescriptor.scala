package reflect

final case class ParameterizedTypeDescriptor(
  rawType: ClassDescriptor,
  typeArguments: Array[TypeDescriptor]
) extends TypeDescriptor {
  
  override def typeName: String =
    s"${rawType.typeName}[${typeArguments.map(_.typeName).mkString(", ")}]"
  
  override def simpleName: String = rawType.simpleName
  override def annotations: Array[Annotation] = rawType.annotations
  override def properties: Array[PropertyDescriptor] = Array.empty
  override def constructors: Array[ConstructorDescriptor] = Array.empty
  override def baseTypes: Array[String] = rawType.baseTypes
  override def typeParameters: Array[TypeDescriptor] = Array.empty
  override def isAbstract: Boolean = rawType.isAbstract
  override def isFinal: Boolean = rawType.isFinal
  override def isCaseClass: Boolean = rawType.isCaseClass
}
