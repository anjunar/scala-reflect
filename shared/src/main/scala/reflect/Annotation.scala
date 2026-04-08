package reflect

final case class Annotation(
  annotationClassName: String,
  parameters: Map[String, Any] = Map.empty
)
