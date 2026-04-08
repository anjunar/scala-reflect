package reflect

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

class ReflectClassLoaderSpec extends AnyFlatSpec with Matchers {

  "ReflectClassLoader" should "load registered classes" in {
    val loader = ReflectClassLoader.create()
    val descriptor = ReflectMacros.reflect[Person]

    loader.register[Person](descriptor)

    val loaded = loader.loadClass("reflect.Person")
    loaded shouldBe defined
    loaded.map(_.typeName) shouldBe Some(descriptor.typeName)
    loaded.map(_.simpleName) shouldBe Some(descriptor.simpleName)
  }

  it should "delegate to parent classloader" in {
    val parent = ReflectClassLoader.create()
    val child = ReflectClassLoader.createWithParent(parent)

    val descriptor = ReflectMacros.reflect[Person]
    parent.register[Person](descriptor)

    val loaded = child.loadClass("reflect.Person")
    loaded shouldBe defined
    loaded.map(_.typeName) shouldBe Some(descriptor.typeName)
    loaded.map(_.simpleName) shouldBe Some(descriptor.simpleName)
  }

  it should "check type assignability" in {
    val loader = ReflectClassLoader.create()
    val personDesc = ReflectMacros.reflect[Person]
    val employeeDesc = ReflectMacros.reflect[Employee]

    loader.register[Person](personDesc)
    loader.register[Employee](employeeDesc)

    loader.isAssignableFrom("reflect.Employee", "reflect.Person") shouldBe true
    loader.isAssignableFrom("reflect.Person", "reflect.Employee") shouldBe false
  }

  it should "find subtypes" in {
    val loader = ReflectClassLoader.create()
    val personDesc = ReflectMacros.reflect[Person]
    val employeeDesc = ReflectMacros.reflect[Employee]

    loader.register[Person](personDesc)
    loader.register[Employee](employeeDesc)

    val subTypes = loader.getSubTypes("reflect.Person")
    subTypes.map(_.simpleName) should contain("Employee")
  }

  it should "create instances via factory" in {
    val loader = ReflectClassLoader.create()
    val descriptor = ReflectMacros.reflect[Person]

    loader.register[Person](descriptor, Some(() => Person("Test", 30)))

    val instance = loader.createInstanceAs[Person]("reflect.Person")
    instance shouldBe defined
    instance.map(_.name) shouldBe Some("Test")
  }

  "ReflectClassLoaderBuilder" should "build classloader with registered classes" in {
    val personDesc = ReflectMacros.reflect[Person]
    val employeeDesc = ReflectMacros.reflect[Employee]

    val loader = ReflectClassLoaderBuilder()
      .register[Person](personDesc)
      .register[Employee](employeeDesc)
      .build()

    loader.loadClass("reflect.Person") shouldBe defined
    loader.loadClass("reflect.Employee") shouldBe defined
  }

  "ReflectClassLoaderWithResources" should "load resources" in {
    val loader = ReflectClassLoaderWithResources()
    loader.addResource("config.json", """{"name": "test"}""")

    loader.getResource("config.json") shouldBe Some("""{"name": "test"}""")
    loader.getResource("nonexistent") shouldBe None
  }

  it should "combine classloading and resources" in {
    val loader = ReflectClassLoaderWithResources()
    val descriptor = ReflectMacros.reflect[Person]

    loader.register[Person](descriptor)
    loader.addResource("person.schema", "Person schema content")

    loader.loadClass("reflect.Person") shouldBe defined
    loader.getResource("person.schema") shouldBe Some("Person schema content")
  }
}

case class Person(name: String, age: Int)

class Employee(name: String, age: Int, val department: String) extends Person(name, age)

case class Container[T](value: T)

class AnnotatedClass {
  @scala.annotation.nowarn("msg=unused")
  val value: String = ""
}

class MutableClass {
  var value: String = ""
}

case class ImmutableClass(value: String)
