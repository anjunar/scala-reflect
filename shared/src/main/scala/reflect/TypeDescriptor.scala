package reflect

trait TypeDescriptor {
  def typeName: String
  def simpleName: String
  def annotations: Array[Annotation]
  def properties: Array[PropertyDescriptor]
  def constructors: Array[ConstructorDescriptor]
  def baseTypes: Array[String]
  def typeParameters: Array[TypeDescriptor]
  def isAbstract: Boolean
  def isFinal: Boolean
  def isCaseClass: Boolean
  
  final def isParameterized: Boolean = typeParameters.nonEmpty
  
  final def hasAnnotation(annotationClass: String): Boolean =
    annotations.exists(_.annotationClassName == annotationClass)
  
  final def getAnnotation(annotationClass: String): Option[Annotation] =
    annotations.find(_.annotationClassName == annotationClass)
}
