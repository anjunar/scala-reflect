# scala-reflect

`scala-reflect` is a small Scala 3 reflection library for JVM and Scala.js projects.
It extracts structural type metadata at compile time and exposes it through lightweight descriptors that you can register, query, and use at runtime.

The library is built around Scala 3 macros instead of Java reflection-heavy APIs, which makes it useful when you want:

- stable type metadata for classes, properties, constructors, and annotations
- property accessors generated from Scala selectors such as `_.name`
- a simple runtime registry for descriptors and factories
- a lightweight classloader-style abstraction for descriptor lookup and subtype queries
- the same API shape on JVM and Scala.js

## Features

- Compile-time generation of `ClassDescriptor`, `PropertyDescriptor`, `ConstructorDescriptor`, and `TypeDescriptor`
- Support for parameterized types and type variables
- Read/write `PropertyAccessor`s generated from selectors
- Global `ReflectRegistry` for registration, lookup, subtype checks, and instance factories
- Local `ReflectClassLoader` instances with optional parent chaining
- `ReflectClassLoaderWithResources` for bundling descriptors and string resources
- `PropertySupport` helpers for schema-style APIs

## Installation

This project is configured as a cross-project for JVM and Scala.js.

If you publish it under the coordinates from `build.sbt`, the dependencies are:

```scala
// JVM
libraryDependencies += "com.anjunar" %% "scala-reflect" % "1.0.0"

// Scala.js
libraryDependencies += "com.anjunar" %%% "scala-reflect" % "1.0.0"
```

## Quick Start

```scala
import reflect.*
import reflect.macros.ReflectMacros

case class Person(name: String, age: Int)

val descriptor = ReflectMacros.reflect[Person]

println(descriptor.typeName)       // your.package.Person
println(descriptor.simpleName)     // Person
println(descriptor.isCaseClass)    // true
println(descriptor.propertyNames.mkString(", "))
```

## Core Concepts

### `ClassDescriptor`

`ClassDescriptor` is the central metadata object. It contains:

- the fully-qualified type name
- the simple class name
- annotations on the class
- reflected properties
- constructor metadata
- base types
- type parameters
- flags such as `isAbstract`, `isFinal`, and `isCaseClass`

Example:

```scala
import reflect.*
import reflect.macros.ReflectMacros

case class Person(name: String, age: Int)

val descriptor = ReflectMacros.reflect[Person]

descriptor.typeName          // e.g. "example.Person"
descriptor.simpleName        // "Person"
descriptor.baseTypes         // Array of inherited type names
descriptor.constructors      // constructor descriptors
descriptor.properties.map(_.name) // Array("name", "age")
```

### `TypeDescriptor`

`ReflectMacros.reflectType[T]` returns a `TypeDescriptor` for arbitrary types, including parameterized ones.

```scala
import reflect.*
import reflect.macros.ReflectMacros

case class Box[T](value: T)

val td = ReflectMacros.reflectType[Box[String]]

println(td.typeName)         // example.Box[scala.Predef.String]
println(td.isParameterized)  // true
```

## Reflecting Classes

### Basic Reflection

```scala
import reflect.macros.ReflectMacros

case class Person(name: String, age: Int)

val descriptor = ReflectMacros.reflect[Person]

val nameProperty =
  descriptor.getProperty("name").getOrElse(sys.error("missing property"))

println(nameProperty.propertyType.typeName)
println(nameProperty.isReadable)
println(nameProperty.isWriteable)
```

### Reflection With Property Accessors

If you want property accessors in the descriptor, use `reflectWithAccessors`.

```scala
import reflect.*
import reflect.macros.ReflectMacros

case class User(var name: String, age: Int)

val descriptor = ReflectMacros.reflectWithAccessors[User]
val accessor =
  descriptor.getPropertyAccessor("name").getOrElse(sys.error("missing accessor"))

val user = User("Ada", 37)
println(accessor.get(user))   // Ada
accessor.set(user, "Grace")
println(user.name)            // Grace
```

## Property Accessors

`ReflectMacros.makeAccessor` builds a typed accessor directly from a selector.

```scala
import reflect.macros.ReflectMacros

case class Settings(var theme: String, version: Int)

val themeAccessor = ReflectMacros.makeAccessor[Settings, String](_.theme)

val settings = Settings("light", 1)
println(themeAccessor.get(settings))   // light

if themeAccessor.hasSetter then
  themeAccessor.set(settings, "dark")
```

For read-only fields, the generated accessor is read-only:

```scala
import reflect.macros.ReflectMacros

case class ReadModel(id: String)

val idAccessor = ReflectMacros.makeAccessor[ReadModel, String](_.id)
println(idAccessor.hasSetter) // false
```

## Property Support For Schema APIs

`PropertySupport` is useful if you want to expose strongly typed property handles in higher-level APIs such as schemas, filters, forms, or query DSLs.

```scala
import reflect.macros.PropertySupport

case class Person(id: String, name: String, age: Int)

val property = PropertySupport.makeProperty[Person, String](_.name)

println(property.name)                 // name
println(property.descriptor.isReadable)
println(property.get(Person("1", "Ada", 37)))
```

You can also extract all available properties including inherited ones:

```scala
import reflect.macros.PropertySupport

trait Audited {
  val createdBy: String = "system"
}

case class Document(id: String, title: String) extends Audited

val properties = PropertySupport.extractPropertiesWithAccessors[Document]
println(properties.map(_.name).mkString(", "))
```

## Working With The Registry

`ReflectRegistry` is the global runtime registry for descriptors.

It can:

- register descriptors
- bind runtime classes
- hold factories for instance creation
- resolve descriptors by type name or simple name
- answer subtype checks

### Registering A Type

```scala
import reflect.*
import reflect.macros.ReflectMacros

case class Query(sql: String)

val descriptor = ReflectMacros.reflect[Query]

ReflectRegistry.clear()
ReflectRegistry.registerByTypeName(
  descriptor.typeName,
  descriptor
    .bindRuntimeClass(classOf[Query])
    .bindFactory(() => Query("select * from dual"))
)

val loaded = ReflectRegistry.loadClass(descriptor.typeName)
val instance = ReflectRegistry.createInstance(descriptor.typeName)

println(loaded.map(_.simpleName))                // Some(Query)
println(instance.map(_.asInstanceOf[Query].sql)) // Some(select * from dual)
```

### Registering With Generated Accessors

If you want the registry to contain descriptors with property accessors, use `register`.

```scala
import reflect.ReflectRegistry

case class User(var name: String, age: Int)

ReflectRegistry.clear()
ReflectRegistry.register(() => User("Ada", 37))

val accessor =
  ReflectRegistry.getPropertyAccessor("your.package.User", "name")
    .getOrElse(sys.error("missing accessor"))
```

## Working With `ReflectClassLoader`

`ReflectClassLoader` is a local, classloader-style abstraction on top of descriptors.

Use it when you want scoped registration instead of relying only on the global registry.

### Register And Load Classes

```scala
import reflect.*
import reflect.macros.ReflectMacros

case class Person(name: String, age: Int)

val loader = ReflectClassLoader.create()
val descriptor = ReflectMacros.reflect[Person]

loader.register[Person](descriptor, () => Person("Ada", 37))

val loaded = loader.loadClass("your.package.Person")
val created = loader.createInstanceAs[Person]("your.package.Person")

println(loaded.map(_.simpleName)) // Some(Person)
println(created.map(_.name))      // Some(Ada)
```

### Parent Classloaders

```scala
import reflect.*
import reflect.macros.ReflectMacros

case class Person(name: String, age: Int)

val parent = ReflectClassLoader.create()
val child = ReflectClassLoader.createWithParent(parent)

parent.register[Person](ReflectMacros.reflect[Person], () => Person("Ada", 37))

println(child.loadClass("your.package.Person").isDefined) // true
```

### Subtype Queries

```scala
import reflect.*
import reflect.macros.ReflectMacros

class Animal(val name: String)
class Dog(name: String) extends Animal(name)

val loader = ReflectClassLoader.create()
loader.register[Animal](ReflectMacros.reflect[Animal])
loader.register[Dog](ReflectMacros.reflect[Dog], () => Dog("Milo"))

println(loader.isAssignableFrom("your.package.Dog", "your.package.Animal")) // true
println(loader.getSubTypes("your.package.Animal").map(_.simpleName))         // List(Dog)
```

## Resources

`ReflectClassLoaderWithResources` combines descriptor loading with a simple string resource store.

```scala
import reflect.*
import reflect.macros.ReflectMacros

case class Person(name: String, age: Int)

val loader = ReflectClassLoaderWithResources()

loader.register[Person](ReflectMacros.reflect[Person], () => Person("Ada", 37))
loader.addResource("person.schema.json", """{"title":"Person"}""")

println(loader.loadClass("your.package.Person").isDefined) // true
println(loader.getResource("person.schema.json"))          // Some(...)
```

## Instance Creation

There are two main ways to create instances:

1. Register a factory on a `ClassDescriptor` and create instances through the registry or classloader.
2. Use `ReflectMacros.createInstance[T](...)` for case classes.

```scala
import reflect.macros.ReflectMacros

case class Person(name: String, age: Int)

val person = ReflectMacros.createInstance[Person]("Ada", 37)
println(person)
```

## Notes And Limitations

- This library targets Scala 3.
- Metadata is generated at compile time through macros.
- `ReflectMacros.createInstance` is intended for case classes.
- Descriptors created with `ReflectMacros.reflect[T]` do not include property accessors by default.
- If you need property accessors inside the descriptor, prefer `ReflectMacros.reflectWithAccessors[T]` or `ReflectRegistry.register`.
- `ReflectRegistry` is global mutable state. Clear it in tests if isolation matters.

## Development

Run the JVM test suite with:

```bash
sbt --batch "scalaReflectJVM/test"
```

## License

Add your license information here.
