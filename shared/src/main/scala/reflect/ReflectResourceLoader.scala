package reflect

import scala.collection.mutable

class ReflectResourceLoader extends ResourceLoader {
  private val resources: mutable.Map[String, String] = mutable.Map.empty

  def addResource(name: String, content: String): Unit = {
    resources += name -> content
  }

  def getResource(name: String): Option[String] = resources.get(name)

  def getResources(name: String): List[String] = {
    resources.collect {
      case (n, content) if n == name => content
    }.toList
  }

  def clear(): Unit = resources.clear()
}
