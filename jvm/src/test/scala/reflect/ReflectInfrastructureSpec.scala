package reflect

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

class ReflectInfrastructureSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  override protected def beforeEach(): Unit = ReflectRegistry.clear()

  override protected def afterEach(): Unit = ReflectRegistry.clear()

  "ReflectRegistry" should "bind runtime classes registered after the descriptor" in {
    val descriptor = ReflectMacros.reflect[RegistryBoundEntity]

    ReflectRegistry.registerByTypeName(descriptor.typeName, descriptor)
    descriptor.runtimeClass shouldBe empty

    ReflectRegistry.registerRuntimeClass(descriptor.typeName, classOf[RegistryBoundEntity])

    val loaded = ReflectRegistry.loadClass(descriptor.typeName).getOrElse(fail("descriptor not found"))
    loaded.runtimeClass shouldBe Some(classOf[RegistryBoundEntity])
  }

  "ReflectClassLoader" should "create instances by simple name when the type name does not match" in {
    val descriptor = ReflectMacros.reflect[LoaderEntity]
    val loader = ReflectClassLoader.create()

    loader.register[LoaderEntity](descriptor, () => LoaderEntity("created"))

    val instance = loader.createInstanceAs[LoaderEntity]("LoaderEntity")
    instance.map(_.value) shouldBe Some("created")
  }

  "ClassDescriptor.maybeResolve" should "return the descriptor itself when it already carries property metadata" in {
    val descriptor = ReflectMacros.reflect[StandaloneEntity]

    ClassDescriptor.maybeResolve(descriptor) shouldBe Some(descriptor)
  }

  "ClassDescriptor.resolve" should "merge missing property accessors from the registered descriptor" in {
    val registered = ReflectMacros.reflectWithAccessors[AccessorEntity]
    ReflectRegistry.registerByTypeName(registered.typeName, registered)

    val withoutAccessor = registered.copy(
      properties = registered.properties.map { property =>
        if property.name == "value" then property.copy(accessor = None) else property
      }
    )

    val resolved = ClassDescriptor.resolve(withoutAccessor)
    val accessor = resolved.getPropertyAccessor("value").getOrElse(fail("accessor not restored"))

    val instance = AccessorEntity("before")
    accessor.get(instance) shouldBe "before"
    accessor.set(instance, "after")
    instance.value shouldBe "after"
  }

  it should "throw for unresolved descriptors without metadata" in {
    val descriptor = ClassDescriptor(
      typeName = "reflect.MissingDescriptor",
      simpleName = "MissingDescriptor",
      annotations = Array.empty,
      properties = Array.empty,
      baseTypes = Array.empty,
      typeParameters = Array.empty,
      constructors = Array.empty,
      isAbstract = false,
      isFinal = false,
      isCaseClass = false
    )

    val error = the[IllegalArgumentException] thrownBy ClassDescriptor.resolve(descriptor)
    error.getMessage should include("Missing class descriptor")
  }

  "PropertyAccessor" should "use the provided setter implementation" in {
    val accessor = PropertyAccessor[MutableValueHolder, String](_.value, _.value = _)
    val instance = MutableValueHolder("start")

    accessor.hasSetter shouldBe true
    accessor.get(instance) shouldBe "start"

    accessor.set(instance, "updated")
    instance.value shouldBe "updated"
  }
}

case class RegistryBoundEntity(value: String)
case class LoaderEntity(value: String)
case class StandaloneEntity(value: String)
case class AccessorEntity(var value: String)
case class MutableValueHolder(var value: String)
