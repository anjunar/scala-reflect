package reflect

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.PropertySupport
import reflect.macros.ReflectMacros

class PropertySupportSpec extends AnyFlatSpec with Matchers {

  case class TestEntity(id: String, name: String, value: Int)

  trait BaseTrait {
    val baseProp: String = "base"
  }

  case class DerivedEntity(id: String, derivedProp: Int) extends BaseTrait

  trait LinksContainer {
    val links: List[String] = List.empty
  }

  case class EntityWithLinks(id: String, override val links: List[String]) extends LinksContainer

  abstract class AbstractBase extends LinksContainer {
    val id: String = ""
  }

  case class ConcreteEntity(override val id: String, name: String) extends AbstractBase

  "PropertySupport.makeProperty" should "extract correct property name from lambda selector" in {
    val property = PropertySupport.makeProperty[TestEntity, String](_.name)
    property.name shouldBe "name"
  }

  it should "extract correct property name for id field" in {
    val property = PropertySupport.makeProperty[TestEntity, String](_.id)
    property.name shouldBe "id"
  }

  it should "extract correct property name for value field" in {
    val property = PropertySupport.makeProperty[TestEntity, Int](_.value)
    property.name shouldBe "value"
  }

  it should "get correct value from instance" in {
    val instance = TestEntity("123", "test", 42)
    val property = PropertySupport.makeProperty[TestEntity, String](_.name)
    property.get(instance) shouldBe "test"
  }

  it should "have correct descriptor" in {
    val property = PropertySupport.makeProperty[TestEntity, String](_.name)
    property.descriptor.name shouldBe "name"
    property.descriptor.isReadable shouldBe true
  }

  it should "extract property from base trait" in {
    val property = PropertySupport.makeProperty[DerivedEntity, String](_.baseProp)
    property.name shouldBe "baseProp"
  }

  it should "extract all properties including inherited" in {
    val properties = PropertySupport.extractPropertiesWithAccessors[DerivedEntity]
    val propertyNames = properties.map(_.name)
    propertyNames should contain("id")
    propertyNames should contain("derivedProp")
    propertyNames should contain("baseProp")
  }

  it should "extract links property from trait" in {
    val property = PropertySupport.makeProperty[EntityWithLinks, List[String]](_.links)
    property.name shouldBe "links"
  }

  it should "extract all properties from entity with links" in {
    val properties = PropertySupport.extractPropertiesWithAccessors[EntityWithLinks]
    val propertyNames = properties.map(_.name)
    propertyNames should contain("id")
    propertyNames should contain("links")
  }

  it should "extract links from abstract base class hierarchy" in {
    val property = PropertySupport.makeProperty[ConcreteEntity, List[String]](_.links)
    property.name shouldBe "links"
  }

  it should "extract all properties from concrete entity" in {
    val properties = PropertySupport.extractPropertiesWithAccessors[ConcreteEntity]
    val propertyNames = properties.map(_.name)
    propertyNames should contain("id")
    propertyNames should contain("name")
    propertyNames should contain("links")
  }

  it should "work with multiple inheritance levels" in {
    trait Level1 { val prop1: String = "p1" }
    abstract class Level2 extends Level1 { val prop2: Int = 1 }
    case class Level3(override val prop1: String, override val prop2: Int, prop3: Boolean) extends Level2

    val properties = PropertySupport.extractPropertiesWithAccessors[Level3]
    val propertyNames = properties.map(_.name)
    propertyNames should contain("prop1")
    propertyNames should contain("prop2")
    propertyNames should contain("prop3")
  }

  it should "work with abstract base schema class" in {
    trait BaseTrait { val baseProp: String = "base" }
    abstract class BaseEntity extends BaseTrait { val id: String = "id" }
    case class MyEntity(override val id: String, name: String) extends BaseEntity

    abstract class EntitySchema[T] {
      protected inline def prop[V](inline selector: T => V): String = {
        val p = PropertySupport.makeProperty(selector)
        p.name
      }
    }

    abstract class AbstractEntitySchema[E <: BaseEntity] extends EntitySchema[E] {
      val idProp: String = prop(_.id)
      val basePropProp: String = prop(_.baseProp)
    }

    class MySchema extends AbstractEntitySchema[MyEntity] {
      val nameProp: String = prop(_.name)
    }

    val schema = new MySchema()
    schema.idProp shouldBe "id"
    schema.basePropProp shouldBe "baseProp"
    schema.nameProp shouldBe "name"
  }

  it should "work with multiple traits and inline def" in {
    trait Trait1 { val prop1: String = "p1" }
    trait Trait2 { val prop2: Int = 2 }
    abstract class Base extends Trait1 { val base: String = "base" }
    case class MultiTraitEntity(override val prop1: String, override val prop2: Int, override val base: String, local: String) extends Base with Trait2

    abstract class Schema[T] {
      protected inline def prop[V](inline selector: T => V): String = {
        val p = PropertySupport.makeProperty(selector)
        p.name
      }
    }

    abstract class AbstractSchema[E <: Base] extends Schema[E] {
      val prop1Prop: String = prop(_.prop1)
      val baseProp: String = prop(_.base)
    }

    class MultiSchema extends AbstractSchema[MultiTraitEntity] {
      val prop2Prop: String = prop(_.prop2)
      val localProp: String = prop(_.local)
    }

    val schema = new MultiSchema()
    schema.prop1Prop shouldBe "prop1"
    schema.baseProp shouldBe "base"
    schema.prop2Prop shouldBe "prop2"
    schema.localProp shouldBe "local"
  }

  it should "restore missing registered properties during descriptor resolution" in {
    case class MergeEntity(id: String, links: List[String], name: String)

    val registered = ReflectMacros.reflectWithAccessors[MergeEntity]
    ReflectRegistry.registerByTypeName(registered.typeName, registered)

    val partial = registered.copy(properties = registered.properties.filterNot(_.name == "links"))
    val resolved = ClassDescriptor.resolve(partial)

    resolved.properties.map(_.name) should contain("id")
    resolved.properties.map(_.name) should contain("name")
    resolved.properties.map(_.name) should contain("links")
  }
}
