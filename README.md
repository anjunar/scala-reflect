# Scala Reflect

Reflection without runtime reflection. Macros describe your types at compile time; at runtime you read
descriptors and call generated accessors, on the JVM and in Scala.js alike.

| Version | Platform | Scala | License |
| --- | --- | --- | --- |
| 1.1.4 | JVM · Scala.js | 3.3 | MIT |

Documentation: [English](https://docs.anjunar.com/en/scala-reflect) · [Deutsch](https://docs.anjunar.com/de/scala-reflect)
Website: [English](https://anjunar.com/en/scala-reflect) · [Deutsch](https://anjunar.com/de/scala-reflect)

## Installation

A cross-built artifact. With sbt 2, `%%` adds the right platform suffix for the JVM and for Scala.js.

```scala
libraryDependencies += "com.anjunar" %% "scala-reflect" % "1.1.4"

// sbt 1, Scala.js:
libraryDependencies += "com.anjunar" %%% "scala-reflect" % "1.1.4"
```

## First example

`reflect` expands to a `ClassDescriptor` at compile time. Everything below is read from it at runtime, without
`java.lang.reflect`.

```scala
package demo

import reflect.macros.ReflectMacros

case class Person(name: String, age: Int)

val descriptor = ReflectMacros.reflect[Person]

descriptor.typeName                                        // demo.Person
descriptor.simpleName                                      // Person
descriptor.isCaseClass                                     // true
descriptor.constructors.head.parameterNames.mkString(", ") // name, age
descriptor.getProperty("age").map(_.propertyType.typeName)
```

To read and write values, take the descriptor with accessors:

```scala
case class User(var name: String, age: Int)

val accessor = ReflectMacros.reflectWithAccessors[User].getPropertyAccessor("name").get

val user = User("Ada", 37)
accessor.get(user)            // Ada
accessor.set(user, "Grace")   // user.name is now Grace
```

## The principle

**01 / Compile time – Metadata from macros.** The structure of a type is read by the compiler, not discovered at
runtime. Nothing is guessed and nothing is missing on Scala.js.

**02 / Typed access – Accessors instead of `Method.invoke`.** Getters and setters are generated code. A read-only
property gets no setter at all.

**03 / One model – The same descriptors everywhere.** Server and browser share one model of your types.
[Scala.js UI](https://github.com/anjunar/scalajs-ui) and [JSON Mapper](https://github.com/anjunar/json-mapper)
build on it.

## Contents

The documentation runs on Scala.js, so every page reflects on real types in your browser.

**Describing**
- [Class descriptors](https://docs.anjunar.com/en/scala-reflect/classes) – properties, constructors, base types and annotations of a class
- [Properties](https://docs.anjunar.com/en/scala-reflect/properties) – type, access, visibility and annotations of one property
- [Constructors and parameters](https://docs.anjunar.com/en/scala-reflect/constructors) – primary and secondary constructors, parameters with defaults
- [Annotations](https://docs.anjunar.com/en/scala-reflect/annotations) – Scala and Java annotations as values, with their arguments
- [Type descriptors](https://docs.anjunar.com/en/scala-reflect/types) – parameterized types, type variables and arrays
- [Generics](https://docs.anjunar.com/en/scala-reflect/generics) – type parameters with bounds and applied type arguments

**Access**
- [Accessors](https://docs.anjunar.com/en/scala-reflect/accessors) – typed getters and setters generated from a selector
- [Properties for APIs](https://docs.anjunar.com/en/scala-reflect/property-support) – property handles for schemas, forms and query builders
- [Creating instances](https://docs.anjunar.com/en/scala-reflect/instances) – case classes from arguments, other classes from factories

**Runtime**
- [Registry](https://docs.anjunar.com/en/scala-reflect/registry) – the global runtime registry: look up, create and check by name
- [Class loaders](https://docs.anjunar.com/en/scala-reflect/class-loaders) – scoped registries with parents, subtype queries and resources
- [Inheritance](https://docs.anjunar.com/en/scala-reflect/inheritance) – base types, subtype lookups and creating by type name

**In practice**
- [A form from a descriptor](https://docs.anjunar.com/en/scala-reflect/form) – input fields generated from writable properties
- [A typed filter API](https://docs.anjunar.com/en/scala-reflect/filters) – named property handles for queries

**Reference**
- [API](https://docs.anjunar.com/en/scala-reflect/api) – macros, descriptors and runtime types at a glance

## Limits

- Scala 3 only. Metadata exists for the types a macro was expanded for; nothing is discovered at runtime.
- `ReflectMacros.reflect[T]` contains no accessors. Use `reflectWithAccessors[T]` or `ReflectRegistry.register`
  when you need to read or write values.
- In 1.1.4 `ReflectMacros.makeAccessor` finds no setter, even for a `var`: its accessor reads, and `set` throws
  `UnsupportedOperationException`. Take the accessor from `reflectWithAccessors` when you need to write.
- A case class descriptor also lists the members the compiler generates for `Product` (`productArity`, `_1`, …).
  For the fields only, go by the primary constructor's `parameterNames`.
- `ReflectRegistry` is global state for the lifetime of the program. Tests that register types call
  `ReflectRegistry.clear()`. A `ReflectClassLoader` orders lookups and holds local factories, but it is no
  isolation boundary: its registrations reach the global registry too.

## Development

Requires a JDK and sbt 2. The build is an sbt project matrix with a JVM and a Scala.js axis; shared sources live in
`shared/`, platform sources in `jvm/` and `js/`.

```bash
sbt --server "Test/testOnly *"
```

### Releasing

Set the version in `build.sbt`, then sign, bundle and upload to the Sonatype Central Portal in one step. The scripts
wait until Maven Central has published the release.

```powershell
.\scripts\publish-central.ps1 -Version 1.1.4
```

```bash
VERSION=1.1.4 scripts/publish-central.sh
```

Credentials come from `SONATYPE_CENTRAL_USERNAME` and `SONATYPE_CENTRAL_PASSWORD`, or from the lines `user=` and
`password=` in `~/.sbt/sonatype_central_credentials`. `-PublishingType USER_MANAGED` (`PUBLISHING_TYPE=USER_MANAGED`)
stops after validation so the release is published by hand in the portal; `-SkipPublishSigned`
(`SKIP_PUBLISH_SIGNED=1`) uploads an existing staging directory again.

## License

Scala Reflect is available under the [MIT License](LICENSE).
