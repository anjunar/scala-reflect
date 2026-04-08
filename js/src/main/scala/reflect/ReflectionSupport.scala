package reflect

object ReflectionSupport {

  def resolveClass(typeName: String): ClassDescriptor =
    ClassDescriptor.forName(typeName)

  def isAssignableFrom(subTypeDescriptor: ClassDescriptor, superTypeDescriptor: ClassDescriptor): Boolean =
    (subTypeDescriptor.runtimeClass, superTypeDescriptor.runtimeClass) match {
      case (Some(subRuntimeClass), Some(superRuntimeClass)) =>
        superRuntimeClass.isAssignableFrom(subRuntimeClass)
      case _ =>
        subTypeDescriptor.typeName == superTypeDescriptor.typeName ||
        subTypeDescriptor.baseTypes.contains(superTypeDescriptor.typeName) ||
        subTypeDescriptor.baseTypes.exists { bt =>
          ClassDescriptor.maybeForName(bt).exists { baseDesc =>
            isAssignableFrom(baseDesc, superTypeDescriptor)
          }
        }
    }
}
