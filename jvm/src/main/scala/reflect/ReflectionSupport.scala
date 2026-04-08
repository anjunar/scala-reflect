package reflect

import scala.collection.mutable

object ReflectionSupport {
  
  private val classCache: mutable.Map[String, Class[?]] = mutable.Map.empty
  
  def resolveClass(name: String): Class[?] =
    classCache.getOrElseUpdate(name, loadClass(name))
  
  private def loadClass(name: String): Class[?] = {
    val underlying = name match {
      case "scala.Int" | "int" => classOf[Int]
      case "scala.Long" | "long" => classOf[Long]
      case "scala.Double" | "double" => classOf[Double]
      case "scala.Float" | "float" => classOf[Float]
      case "scala.Boolean" | "boolean" => classOf[Boolean]
      case "scala.Byte" | "byte" => classOf[Byte]
      case "scala.Short" | "short" => classOf[Short]
      case "scala.Char" | "char" => classOf[Char]
      case "scala.Unit" | "void" => classOf[Unit]
      case "scala.Any" | "scala.AnyRef" => classOf[Any]
      case other => Class.forName(other)
    }
    underlying
  }
  
  def isAssignableFrom(subType: Class[?], superType: Class[?]): Boolean =
    superType.isAssignableFrom(subType)
}
