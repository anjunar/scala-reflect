package reflect

import scala.collection.mutable

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
