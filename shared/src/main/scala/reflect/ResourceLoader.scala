package reflect

import scala.collection.mutable

trait ResourceLoader {
  def getResource(name: String): Option[String]
  def getResources(name: String): List[String]
}
