package reflect

final case class ParameterDescriptor(
  name: String,
  parameterType: TypeDescriptor,
  annotations: Array[Annotation],
  hasDefault: Boolean,
  defaultIndex: Int
) {
  
  def hasAnnotation(annotationClass: String): Boolean =
    annotations.exists(_.annotationClassName == annotationClass)
  
  def getAnnotation(annotationClass: String): Option[Annotation] =
    annotations.find(_.annotationClassName == annotationClass)
}
