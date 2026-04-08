package reflect

import scala.collection.mutable
import scala.reflect.ClassTag

class ReflectClassLoader(
  parent: Option[ReflectClassLoader] = None,
  private val localRegistry: mutable.Map[String, ClassDescriptor] = mutable.Map.empty
) extends ClassLoaderLike {

  private def localDescriptors: Iterable[ClassDescriptor] =
    localRegistry.values.map(_.resolved)

  def register[T](descriptor: ClassDescriptor, factory: Option[() => T] = None)(using ClassTag[T]): Unit = {
    val typeName = descriptor.typeName
    descriptor.bindRuntimeClass(summon[ClassTag[T]].runtimeClass)
    factory.foreach(f => descriptor.bindFactory(f.asInstanceOf[() => Any]))
    localRegistry += typeName -> descriptor
    ReflectRegistry.registerByTypeName(typeName, descriptor)
  }

  def register[T](descriptor: ClassDescriptor, factory: () => T)(using ClassTag[T]): Unit = {
    val typeName = descriptor.typeName
    descriptor.bindRuntimeClass(summon[ClassTag[T]].runtimeClass)
    descriptor.bindFactory(factory.asInstanceOf[() => Any])
    localRegistry += typeName -> descriptor
    ReflectRegistry.registerByTypeName(typeName, descriptor)
  }

  def registerByTypeName(typeName: String, descriptor: ClassDescriptor): Unit = {
    localRegistry += typeName -> descriptor
    ReflectRegistry.registerByTypeName(typeName, descriptor)
  }

  def loadClass(typeName: String): Option[ClassDescriptor] = {
    localRegistry.get(typeName)
      .map(_.resolved)
      .orElse(parent.flatMap(_.loadClass(typeName)))
      .orElse(ClassDescriptor.maybeForName(typeName))
  }

  def loadClass[T](using Manifest[T]): Option[ClassDescriptor] =
    loadClass(manifest[T].runtimeClass.getName)

  def loadClassBySimpleName(simpleName: String): Option[ClassDescriptor] = {
    localRegistry.values.find(_.simpleName == simpleName)
      .map(_.resolved)
      .orElse(parent.flatMap(_.loadClassBySimpleName(simpleName)))
      .orElse(ClassDescriptor.maybeForSimpleName(simpleName))
  }

  def createInstance(typeName: String): Option[Any] =
    loadClass(typeName).flatMap(_.newInstance())
      .orElse(loadClassBySimpleName(typeName).flatMap(_.newInstance()))

  def createInstanceAs[T](typeName: String): Option[T] =
    createInstance(typeName).map(_.asInstanceOf[T])

  def isAssignableFrom(subType: String, superType: String): Boolean =
    if subType == superType then true
    else loadClass(subType).exists(_.isAssignableTo(superType))

  def isAssignableFrom[T](subType: String)(using Manifest[T]): Boolean =
    isAssignableFrom(subType, manifest[T].runtimeClass.getName)

  def getSubTypes(superType: String): List[ClassDescriptor] = {
    val local = localDescriptors.filter(_.isSubTypeOf(superType)).toList
    val parentSubs = parent.map(_.getSubTypes(superType)).getOrElse(Nil)
    val registrySubs = ClassDescriptor.subTypesOf(superType)
    (local ++ parentSubs ++ registrySubs).distinct
  }

  def getSubTypes[T](using Manifest[T]): List[ClassDescriptor] =
    getSubTypes(manifest[T].runtimeClass.getName)

  def getAllRegistered: Iterable[ClassDescriptor] = {
    val local = localDescriptors
    val parentTypes = parent.map(_.getAllRegistered).getOrElse(Nil)
    (local ++ parentTypes ++ ClassDescriptor.all).toList.distinctBy(_.typeName)
  }

  def contains(typeName: String): Boolean =
    localRegistry.contains(typeName) ||
    parent.exists(_.contains(typeName)) ||
    ClassDescriptor.maybeForName(typeName).nonEmpty ||
    ClassDescriptor.maybeForSimpleName(typeName).nonEmpty

  def clearLocal(): Unit = {
    localRegistry.clear()
  }

  def withParent(newParent: ReflectClassLoader): ReflectClassLoader =
    new ReflectClassLoader(Some(newParent), localRegistry.clone())
}

object ReflectClassLoader {

  private val defaultInstance = new ReflectClassLoader()

  def default: ReflectClassLoader = defaultInstance

  def create(): ReflectClassLoader = new ReflectClassLoader()

  def createWithParent(parent: ReflectClassLoader): ReflectClassLoader =
    new ReflectClassLoader(Some(parent))

  def apply(parent: Option[ReflectClassLoader] = None): ReflectClassLoader =
    new ReflectClassLoader(parent)
}
