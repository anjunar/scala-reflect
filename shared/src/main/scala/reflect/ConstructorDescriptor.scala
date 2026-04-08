package reflect

final case class ConstructorDescriptor(
  parameters: Array[ParameterDescriptor],
  annotations: Array[Annotation],
  isPrimary: Boolean,
  isPrivate: Boolean
) {
  
  def parameterCount: Int = parameters.length
  
  def parameterNames: Array[String] = parameters.map(_.name)
}
