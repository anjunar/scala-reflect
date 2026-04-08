package reflect.macros

import scala.quoted.*
import reflect.{PropertyAccessor, PropertyDescriptor}

object PropertySupport {

  inline def makeProperty[T, V](inline selector: T => V): PropertyWithAccessor[T, V] =
    ${ makePropertyImpl[T, V]('selector) }

  inline def extractPropertiesWithAccessors[T]: Array[PropertyWithAccessor[T, Any]] =
    ${ extractPropertiesWithAccessorsImpl[T] }

  private def makePropertyImpl[T: Type, V: Type](using Quotes)(selectorExpr: Expr[T => V]): Expr[PropertyWithAccessor[T, V]] = {
    import quotes.reflect.*

    val accessorExpr = ReflectMacros.makeAccessorImpl[T, V](selectorExpr)
    val selectedProperty = extractPropertyName(selectorExpr)
    val classDescriptorExpr = ReflectMacros.reflectImpl[T]

    val propertyDescriptorExpr = '{
      val cd = $classDescriptorExpr
      cd.properties.find(_.name == ${ Expr(selectedProperty) }).getOrElse(
        throw new IllegalStateException(s"Property ${${ Expr(selectedProperty) }} not found")
      )
    }

    '{
      new PropertyWithAccessor[T, V]($accessorExpr, $propertyDescriptorExpr)
    }
  }

  private def extractPropertyName[T: Type, V: Type](using Quotes)(selectorExpr: Expr[T => V]): String = {
    import quotes.reflect.*

    final case class SelectedProperty(name: String, symbol: Symbol)

    def selectedPropertyFromBody(body: Term, paramSym: Symbol): SelectedProperty = body match {
      case Inlined(_, _, inner) =>
        selectedPropertyFromBody(inner, paramSym)
      case Typed(inner, _) =>
        selectedPropertyFromBody(inner, paramSym)
      case Select(qualifier, fieldName) =>
        val unwrappedQualifier = unwrapTerm(qualifier)
        if unwrappedQualifier.symbol == paramSym then
          SelectedProperty(fieldName, body.symbol)
        else
          report.errorAndAbort(
            s"Expected a simple field selection such as _.name, but found:\n${body.show(using Printer.TreeStructure)}"
          )
      case _ =>
        report.errorAndAbort(
          s"Expected a simple field selection such as _.name, but found:\n${body.show(using Printer.TreeStructure)}"
        )
    }

    def propertyFromSelector(expr: Expr[T => V]): SelectedProperty = {
      def loop(term: Term, localDefs: Map[String, DefDef]): SelectedProperty = term match {
        case Inlined(_, _, inner) =>
          loop(inner, localDefs)
        case Block(stats, expr) =>
          val defs = stats.collect { case dd: DefDef => dd.name -> dd }.toMap
          loop(expr, localDefs ++ defs)
        case Lambda(List(param), body) =>
          selectedPropertyFromBody(body, param.symbol)
        case Closure(Ident(name), _) =>
          localDefs.get(name) match {
            case Some(dd) =>
              dd.rhs match {
                case Some(rhs) =>
                  val params = dd.termParamss.flatMap(_.params)
                  params match {
                    case param :: Nil =>
                      selectedPropertyFromBody(rhs, param.symbol)
                    case _ =>
                      report.errorAndAbort(s"Closure $name does not have exactly one parameter")
                  }
                case None =>
                  report.errorAndAbort(s"Closure $name has no body")
              }
            case None =>
              report.errorAndAbort(s"Could not resolve closure $name")
          }
        case _ =>
          report.errorAndAbort(
            s"Expected a simple selector such as _.name, but found:\n${term.show(using Printer.TreeStructure)}"
          )
      }
      loop(expr.asTerm, Map.empty)
    }

    propertyFromSelector(selectorExpr).name
  }

  @scala.annotation.tailrec
  private def unwrapTerm(using Quotes)(term: quotes.reflect.Term): quotes.reflect.Term = {
    import quotes.reflect.*
    term match {
      case Inlined(_, _, inner) => unwrapTerm(inner)
      case Typed(inner, _)      => unwrapTerm(inner)
      case Block(Nil, expr)     => unwrapTerm(expr)
      case other                => other
    }
  }

  private def extractPropertiesWithAccessorsImpl[T: Type](using Quotes): Expr[Array[PropertyWithAccessor[T, Any]]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val propertySymbols = collectPropertySymbols(tpe)

    val propertyWithAccessors = propertySymbols.map { s =>
      val name = s.name
      val propertyType = ReflectMacros.normalizeType(tpe.memberType(s))
      propertyType.asType match {
        case '[v] =>
          val getterExpr = '{ (t: T) => ${ Select('{ t }.asTerm, s).asExprOf[v] } }
          val setterName = s"${name}_="
          val setterExists = tpe.typeSymbol.methodMember(setterName).nonEmpty ||
            tpe.baseClasses.exists(_.methodMember(setterName).nonEmpty)

          val accessorExpr =
            if setterExists then
              val setterSym = tpe.typeSymbol.methodMember(setterName).headOption
                .orElse(tpe.baseClasses.flatMap(_.methodMember(setterName)).headOption)
              setterSym match {
                case Some(sym) =>
                  val setterExpr = '{
                    (t: T, v: v) => ${ Select('{ t }.asTerm, sym).appliedTo('{ v }.asTerm).asExprOf[Unit] }
                  }
                  '{ PropertyAccessor($getterExpr, $setterExpr) }
                case None =>
                  '{ PropertyAccessor.readOnly($getterExpr) }
              }
            else
              '{ PropertyAccessor.readOnly($getterExpr) }

          val descriptorExpr = '{
            val cd = ${ ReflectMacros.reflectImpl[T] }
            cd.properties.find(_.name == ${ Expr(name) }).getOrElse(
              throw new IllegalStateException(s"Property ${${ Expr(name) }} not found")
            )
          }

          '{
            new PropertyWithAccessor[T, Any]($accessorExpr.asInstanceOf[PropertyAccessor[T, Any]], $descriptorExpr)
          }
      }
    }

    '{ Array(${ Varargs(propertyWithAccessors) }*) }
  }

  private def collectPropertySymbols(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.Symbol] = {
    import quotes.reflect.*

    // Collect property symbols from the class and all base classes
    val allSymbols = tpe.baseClasses.flatMap { baseClass =>
      baseClass.declarations.collect {
        case s: Symbol if s.isTerm => s
      }.filter { s =>
        !s.flags.is(Flags.Private) && !s.flags.is(Flags.Protected) &&
        !s.name.contains("$") &&
        !s.name.endsWith("_=") &&
        s.name != "hashCode" && s.name != "toString" && s.name != "getClass" && s.name != "clone" && s.name != "notify" && s.name != "notifyAll" && s.name != "wait" && s.name != "finalize" &&
        !s.isClassConstructor &&
        !s.flags.is(Flags.Macro) &&
        !s.flags.is(Flags.Artifact)
      }.filter { s =>
        val isField = !s.isDefDef
        val isMethod = s.isDefDef

        if isField then true
        else if isMethod then
          val methodType = ReflectMacros.normalizeType(tpe.memberType(s))
          val isParameterless = s.paramSymss.isEmpty
          val returnsUnit = methodType =:= TypeRepr.of[Unit]
          isParameterless && !returnsUnit
        else false
      }
    }
    allSymbols
      .groupBy(_.name)
      .values
      .map { symbols =>
        symbols.maxBy { symbol =>
          (
            if symbol.annotations.nonEmpty then 1 else 0,
            if !symbol.isDefDef then 1 else 0
          )
        }
      }
      .toList
  }
}

final case class PropertyWithAccessor[T, V](
  accessor: PropertyAccessor[T, V],
  descriptor: PropertyDescriptor
) {
  def name: String = descriptor.name
  def isWriteable: Boolean = descriptor.isWriteable
  def get(instance: T): V = accessor.get(instance)
  def set(instance: T, value: V): Unit = accessor.set(instance, value)
}
