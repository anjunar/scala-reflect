package reflect

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

class ReflectClassLoaderApiSpec extends AnyFlatSpec with Matchers {

  "ReflectClassLoaderBuilder" should "allow registration without Manifest" in {
    val descriptor = ReflectMacros.reflect[Table[String]]

    val loader = ReflectClassLoaderBuilder()
      .register[Table[String]](descriptor, () => Table[String]())
      .build()

    loader.loadClass(descriptor.typeName) shouldBe defined
  }

  it should "allow registration of existentially typed classes without Manifest" in {
    val descriptor = ReflectMacros.reflect[Table[?]]

    val loader = ReflectClassLoaderBuilder()
      .register[Table[?]](descriptor, () => Table[String]())
      .build()

    loader.loadClass(descriptor.typeName) shouldBe defined
  }

  it should "create instances from existentially typed registrations" in {
    val descriptor = ReflectMacros.reflect[Table[?]]

    val loader = ReflectClassLoaderBuilder()
      .register[Table[?]](descriptor, () => Table[Int]())
      .build()

    val instance = loader.createInstanceAs[Table[?]](descriptor.typeName)
    instance shouldBe defined
  }

  "ReflectClassLoader" should "allow direct registration without Manifest" in {
    val descriptor = ReflectMacros.reflect[Document]

    val loader = ReflectClassLoader.create()
    loader.register[Document](descriptor, () => Document("test"))

    loader.loadClass(descriptor.typeName) shouldBe defined
    loader.createInstanceAs[Document](descriptor.typeName).map(_.content) shouldBe Some("test")
  }

  "ReflectRegistry" should "allow registration without Manifest" in {
    val descriptor = ReflectMacros.reflect[Query]

    ReflectRegistry.clear()
    ReflectRegistry.registerByTypeName(
      descriptor.typeName,
      descriptor
        .bindRuntimeClass(classOf[Query])
        .bindFactory(() => Query("select *"))
    )

    ReflectRegistry.loadClass(descriptor.typeName) shouldBe defined
    ReflectRegistry.createInstance(descriptor.typeName).map(_.asInstanceOf[Query].sql) shouldBe Some("select *")

    ReflectRegistry.clear()
  }
}

case class Table[T]()
case class Document(content: String)
case class Query(sql: String)
