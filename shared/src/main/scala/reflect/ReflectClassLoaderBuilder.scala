package reflect

import scala.collection.mutable

class ReflectClassLoaderBuilder {
  private val descriptors: mutable.Map[String, ClassDescriptor] = mutable.Map.empty
  private var parent: Option[ReflectClassLoader] = None

  def withParent(p: ReflectClassLoader): this.type = {
    parent = Some(p)
    this
  }

  def register[T](descriptor: ClassDescriptor, factory: Option[() => T] = None)(using Manifest[T]): this.type = {
    val typeName = descriptor.typeName
    descriptors += typeName -> descriptor
    factory.foreach(f => descriptor.bindFactory(f.asInstanceOf[() => Any]))
    this
  }

  def register[T](descriptor: ClassDescriptor, factory: () => T): this.type = {
    val typeName = descriptor.typeName
    descriptors += typeName -> descriptor
    descriptor.bindFactory(factory.asInstanceOf[() => Any])
    this
  }

  def registerByTypeName(typeName: String, descriptor: ClassDescriptor): this.type = {
    descriptors += typeName -> descriptor
    this
  }

  def registerAll(descriptors: Seq[ClassDescriptor]): this.type = {
    descriptors.foreach(d => registerByTypeName(d.typeName, d))
    this
  }

  def build(): ReflectClassLoader = {
    val loader = new ReflectClassLoader(parent, descriptors.clone())
    loader
  }
}

object ReflectClassLoaderBuilder {
  def apply(): ReflectClassLoaderBuilder = new ReflectClassLoaderBuilder()
}
