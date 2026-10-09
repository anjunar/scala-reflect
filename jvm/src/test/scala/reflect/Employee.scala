package reflect

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

class Employee(name: String, age: Int, val department: String) extends Person(name, age)
