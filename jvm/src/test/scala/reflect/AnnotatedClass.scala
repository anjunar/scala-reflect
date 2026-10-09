package reflect

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros

class AnnotatedClass {
  @scala.annotation.nowarn("msg=unused")
  val value: String = ""
}
