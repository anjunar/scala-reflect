package reflect

final case class PropertyDescriptor(
  name: String,
  propertyType: TypeDescriptor,
  annotations: Array[Annotation],
  isWriteable: Boolean,
  isReadable: Boolean,
  isPublic: Boolean,
  isPrivate: Boolean,
  isProtected: Boolean,
  accessor: Option[PropertyAccessor[Any, Any]]
) {

  def hasAnnotation(annotationClass: String): Boolean =
    annotations.exists(_.annotationClassName == annotationClass)

  def getAnnotation(annotationClass: String): Option[Annotation] =
    annotations.find(_.annotationClassName == annotationClass)
}
