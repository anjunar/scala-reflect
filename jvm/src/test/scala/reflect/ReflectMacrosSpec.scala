package reflect

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

class ReflectMacrosSpec extends AnyFlatSpec with Matchers {

  "ReflectMacros.reflect" should "extract class descriptor for simple case class" in {
    val descriptor = ReflectMacros.reflect[Person]

    descriptor.typeName shouldBe "reflect.Person"
    descriptor.simpleName shouldBe "Person"
    descriptor.isCaseClass shouldBe true
    descriptor.properties.length should be >= 2
  }

  it should "extract properties with correct types" in {
    val descriptor = ReflectMacros.reflect[Person]

    val nameProp = descriptor.getProperty("name").getOrElse(fail("name property not found"))
    nameProp.propertyType.typeName should endWith("String")
    nameProp.isWriteable shouldBe false
    nameProp.isReadable shouldBe true

    val ageProp = descriptor.getProperty("age").getOrElse(fail("age property not found"))
    ageProp.propertyType.typeName should endWith("Int")
  }

  it should "extract base types" in {
    val descriptor = ReflectMacros.reflect[Person]

    descriptor.baseTypes should contain("scala.Product")
  }

  it should "extract annotations from class" in {
    val descriptor = ReflectMacros.reflect[AnnotatedClass]

    descriptor.annotations should not be empty
  }

  it should "extract annotations from properties" in {
    val descriptor = ReflectMacros.reflect[AnnotatedClass]

    val valueProp = descriptor.getProperty("value").getOrElse(fail("value property not found"))
    valueProp.annotations should not be empty
  }

  "ReflectMacros.reflectType" should "handle parameterized types" in {
    val descriptor = ReflectMacros.reflectType[Container[String]]

    descriptor shouldBe a[ParameterizedTypeDescriptor]
    descriptor.typeName should startWith("reflect.Container[")
  }

  it should "handle array types" in {
    val descriptor = ReflectMacros.reflectType[Array[Int]]

    // Array wird als ParameterizedTypeDescriptor dargestellt, da Scala Arrays parametrisiert sind
    descriptor shouldBe a[ParameterizedTypeDescriptor]
  }

  "ReflectRegistry" should "register and load class descriptors" in {
    val descriptor = ReflectMacros.reflect[Person]

    ReflectRegistry.clear()
    ReflectRegistry.registerByTypeName(
      descriptor.typeName,
      descriptor.bindRuntimeClass(classOf[Person])
    )

    ReflectRegistry.loadClass("reflect.Person") shouldBe Some(descriptor)
    ReflectRegistry.loadClassBySimpleName("Person") shouldBe Some(descriptor)
    ReflectRegistry.clear()
  }

  it should "check type assignability" in {
    ReflectRegistry.clear()

    val personDescriptor =
      ReflectMacros.reflect[Person].bindRuntimeClass(classOf[Person])
    val employeeDescriptor =
      ReflectMacros.reflect[Employee].bindRuntimeClass(classOf[Employee])

    ReflectRegistry.registerByTypeName(personDescriptor.typeName, personDescriptor)
    ReflectRegistry.registerByTypeName(employeeDescriptor.typeName, employeeDescriptor)

    ReflectRegistry.isAssignableFrom("reflect.Employee", "reflect.Person") shouldBe true
    ReflectRegistry.isAssignableFrom("reflect.Person", "reflect.Employee") shouldBe false
    ReflectRegistry.clear()
  }

  "PropertyAccessor" should "create read-only accessor for val" in {
    val obj = ImmutableClass("test")

    val accessor = ReflectMacros.makeAccessor[ImmutableClass, String](_.value)

    accessor.get(obj) shouldBe "test"
    accessor.hasSetter shouldBe false

    assertThrows[UnsupportedOperationException] {
      accessor.set(obj, "updated")
    }
  }
}
