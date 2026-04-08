package reflect

import scala.collection.mutable

trait ClassLoaderLike {
  def loadClass(typeName: String): Option[ClassDescriptor]
  def loadClass[T](using Manifest[T]): Option[ClassDescriptor]
  def createInstance(typeName: String): Option[Any]
  def createInstanceAs[T](typeName: String): Option[T]
  def isAssignableFrom(subType: String, superType: String): Boolean
  def getSubTypes(superType: String): List[ClassDescriptor]
  def getAllRegistered: Iterable[ClassDescriptor]
  def contains(typeName: String): Boolean
}
