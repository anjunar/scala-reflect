package reflect

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros
import scala.annotation.nowarn

class AnnotatedClass {
  @nowarn("msg=unused")
  val value: String = ""
}
