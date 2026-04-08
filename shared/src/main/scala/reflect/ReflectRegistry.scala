package reflect

import scala.collection.mutable
import scala.reflect.ClassTag

object ReflectRegistry {

  private val descriptorsByName: mutable.Map[String, ClassDescriptor] = mutable.Map.empty
  private val descriptorsBySimpleName: mutable.Map[String, ClassDescriptor] = mutable.Map.empty
  private val runtimeClassesByName: mutable.Map[String, Class[?]] = mutable.Map.empty
  private val runtimeClassesBySimpleName: mutable.Map[String, Class[?]] = mutable.Map.empty

  inline def register[T](inline factory: () => T)(using ClassTag[T]): ClassDescriptor = {
    val descriptor = reflect.macros.ReflectMacros.reflectWithAccessors[T]
    bindRuntimeClass(descriptor, summon[ClassTag[T]].runtimeClass)
    descriptor.bindFactory(factory.asInstanceOf[() => Any])
    registerWithAccessors(descriptor)
    descriptor
  }

  def registerWithAccessors[T](descriptor: ClassDescriptor): Unit = {
    val typeName = descriptor.typeName
    descriptorsByName += typeName -> descriptor

    val simpleName = descriptor.simpleName
    if !descriptorsBySimpleName.contains(simpleName) then
      descriptorsBySimpleName += simpleName -> descriptor

    bindKnownRuntimeClass(descriptor)
  }

  def registerByTypeName(typeName: String, descriptor: ClassDescriptor): Unit = {
    descriptorsByName += typeName -> descriptor

    val simpleName = descriptor.simpleName
    if !descriptorsBySimpleName.contains(simpleName) then
      descriptorsBySimpleName += simpleName -> descriptor

    bindKnownRuntimeClass(descriptor)
  }

  def loadClass(typeName: String): Option[ClassDescriptor] =
    descriptorsByName.get(typeName).orElse(descriptorsBySimpleName.get(typeName)).map(bindKnownRuntimeClass)

  def loadClassBySimpleName(simpleName: String): Option[ClassDescriptor] =
    descriptorsBySimpleName.get(simpleName).map(bindKnownRuntimeClass)

  def registerRuntimeClass(typeName: String, runtimeClass: Class[?]): Unit = {
    runtimeClassesByName += typeName -> runtimeClass
    val simpleName = runtimeClass.getSimpleName
    if !runtimeClassesBySimpleName.contains(simpleName) then
      runtimeClassesBySimpleName += simpleName -> runtimeClass
    descriptorsByName.get(typeName).foreach(_.bindRuntimeClass(runtimeClass))
    descriptorsBySimpleName.get(simpleName).foreach(_.bindRuntimeClass(runtimeClass))
  }

  def createInstance(typeName: String): Option[Any] =
    loadClass(typeName).flatMap(_.createInstance())

  def getPropertyAccessor(typeName: String, propertyName: String): Option[PropertyAccessor[Any, Any]] =
    descriptorsByName.get(typeName).flatMap(_.properties.find(_.name == propertyName).flatMap(_.accessor))

  def isAssignableFrom(subType: String, superType: String): Boolean = {
    if subType == superType then return true

    loadClass(subType) match {
      case Some(subDescriptor) =>
        loadClass(superType) match {
          case Some(superDescriptor) =>
            (subDescriptor.runtimeClass, superDescriptor.runtimeClass) match {
              case (Some(subRuntimeClass), Some(superRuntimeClass)) =>
                superRuntimeClass.isAssignableFrom(subRuntimeClass)
              case _ =>
                subDescriptor.baseTypes.contains(superType) || subDescriptor.baseTypes.exists(bt => isAssignableFrom(bt, superType))
            }
          case None =>
            subDescriptor.baseTypes.contains(superType) || subDescriptor.baseTypes.exists(bt => isAssignableFrom(bt, superType))
        }
      case None =>
        false
    }
  }

  def getSubTypes(superType: String): List[ClassDescriptor] =
    descriptorsByName.values.filter(_.baseTypes.contains(superType)).toList.map(bindKnownRuntimeClass)

  def getAllRegistered: Iterable[ClassDescriptor] = descriptorsByName.values.map(bindKnownRuntimeClass)

  def contains(typeName: String): Boolean = descriptorsByName.contains(typeName)

  def clear(): Unit = {
    descriptorsByName.clear()
    descriptorsBySimpleName.clear()
    runtimeClassesByName.clear()
    runtimeClassesBySimpleName.clear()
  }

  private def bindKnownRuntimeClass(descriptor: ClassDescriptor): ClassDescriptor = {
    if descriptor.runtimeClass.isEmpty then
      runtimeClassesByName.get(descriptor.typeName)
        .orElse(runtimeClassesBySimpleName.get(descriptor.simpleName))
        .foreach(descriptor.bindRuntimeClass)
    descriptor
  }

  private def bindRuntimeClass(descriptor: ClassDescriptor, runtimeClass: Class[?]): ClassDescriptor = {
    registerRuntimeClass(descriptor.typeName, runtimeClass)
    descriptor.bindRuntimeClass(runtimeClass)
  }

}
