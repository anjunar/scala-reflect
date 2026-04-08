package reflect

import scala.collection.mutable

trait ResourceLoader {
  def getResource(name: String): Option[String]
  def getResources(name: String): List[String]
}

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

class ReflectClassLoaderWithResources(
  parent: Option[ReflectClassLoader] = None,
  private val localRegistry: mutable.Map[String, ClassDescriptor] = mutable.Map.empty,
  private val resourceLoader: ReflectResourceLoader = new ReflectResourceLoader
) extends ReflectClassLoader(parent, localRegistry) with ResourceLoader {

  def getResource(name: String): Option[String] = resourceLoader.getResource(name)

  def getResources(name: String): List[String] = resourceLoader.getResources(name)

  def addResource(name: String, content: String): Unit = resourceLoader.addResource(name, content)

  override def withParent(newParent: ReflectClassLoader): ReflectClassLoaderWithResources = {
    new ReflectClassLoaderWithResources(
      Some(newParent),
      localRegistry.clone(),
      resourceLoader
    )
  }
}

object ReflectClassLoaderWithResources {
  def apply(): ReflectClassLoaderWithResources = new ReflectClassLoaderWithResources()

  def withParent(parent: ReflectClassLoader): ReflectClassLoaderWithResources =
    new ReflectClassLoaderWithResources(Some(parent))
}
