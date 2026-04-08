package reflect.macros

import scala.quoted.*
import reflect.*

object ReflectMacros {

  inline def reflect[T]: ClassDescriptor = ${ reflectImpl[T] }

  inline def reflectType[T]: TypeDescriptor = ${ reflectTypeImpl[T] }

  inline def extractProperties[T]: Array[PropertyDescriptor] = ${ extractPropertiesImpl[T] }

  inline def extractConstructors[T]: Array[ConstructorDescriptor] = ${ extractConstructorsImpl[T] }

  inline def reflectWithAccessors[T]: ClassDescriptor = ${ reflectWithAccessorsImpl[T] }

  inline def makeAccessor[E, V](inline selector: E => V): PropertyAccessor[E, V] =
    ${ makeAccessorImpl[E, V]('selector) }

  inline def createInstance[T](inline args: Any*): T = ${ createInstanceImpl[T]('args) }

  private[macros] def reflectImpl[T: Type](using Quotes): Expr[ClassDescriptor] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    val typeNameExpr = Expr(symbol.fullName)
    val simpleNameExpr = Expr(symbol.name)
    val annotationsExpr = extractAnnotations(symbol)
    val propertiesExpr = extractPropertiesImpl[T]
    val baseTypesExpr = extractBaseTypes(tpe)
    val typeParamsExpr = extractTypeParameters(symbol)
    val constructorsExpr = extractConstructorsImpl[T]
    val isAbstractExpr = Expr(symbol.flags.is(Flags.Abstract))
    val isFinalExpr = Expr(symbol.flags.is(Flags.Final))
    val isCaseClassExpr = Expr(symbol.flags.is(Flags.Case))

    '{
      ClassDescriptor(
        typeName = $typeNameExpr,
        simpleName = $simpleNameExpr,
        annotations = $annotationsExpr,
        properties = $propertiesExpr,
        baseTypes = $baseTypesExpr,
        typeParameters = $typeParamsExpr,
        constructors = $constructorsExpr,
        isAbstract = $isAbstractExpr,
        isFinal = $isFinalExpr,
        isCaseClass = $isCaseClassExpr
      )
    }
  }

  private[macros] def reflectWithAccessorsImpl[T: Type](using Quotes): Expr[ClassDescriptor] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    val typeNameExpr = Expr(symbol.fullName)
    val simpleNameExpr = Expr(symbol.name)
    val annotationsExpr = extractAnnotations(symbol)
    val propertiesExpr = extractPropertiesWithAccessorsImpl[T]
    val baseTypesExpr = extractBaseTypes(tpe)
    val typeParamsExpr = extractTypeParameters(symbol)
    val constructorsExpr = extractConstructorsImpl[T]
    val isAbstractExpr = Expr(symbol.flags.is(Flags.Abstract))
    val isFinalExpr = Expr(symbol.flags.is(Flags.Final))
    val isCaseClassExpr = Expr(symbol.flags.is(Flags.Case))

    '{
      ClassDescriptor(
        typeName = $typeNameExpr,
        simpleName = $simpleNameExpr,
        annotations = $annotationsExpr,
        properties = $propertiesExpr,
        baseTypes = $baseTypesExpr,
        typeParameters = $typeParamsExpr,
        constructors = $constructorsExpr,
        isAbstract = $isAbstractExpr,
        isFinal = $isFinalExpr,
        isCaseClass = $isCaseClassExpr
      )
    }
  }

  private[macros] def extractPropertiesWithAccessorsImpl[T: Type](using Quotes): Expr[Array[PropertyDescriptor]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val propertySymbols = collectPropertySymbols(tpe)

    val propertyDescriptors = propertySymbols.map { s =>
      val nameExpr = Expr(s.name)
      val propertyType = normalizeType(tpe.memberType(s))
      val propertyTypeExpr = buildTypeDescriptorSimple(propertyType)
      val annotationsExpr = extractAnnotations(s)

      val isWriteable = tpe.typeSymbol.methodMember(s"${s.name}_=").nonEmpty ||
        tpe.baseClasses.exists(_.methodMember(s"${s.name}_=").nonEmpty)
      val isWriteableExpr = Expr(isWriteable)

      val isPublicExpr = Expr(!s.flags.is(Flags.Private) && !s.flags.is(Flags.Protected))
      val isPrivateExpr = Expr(s.flags.is(Flags.Private))
      val isProtectedExpr = Expr(s.flags.is(Flags.Protected))
      val isReadableExpr = Expr(!s.flags.is(Flags.Private))

      propertyType.asType match {
        case '[v] =>
          val getterExpr = '{ (t: T) => ${ Select('{ t }.asTerm, s).asExprOf[v] } }

          val setterName = s"${s.name}_="
          val setterExists = isWriteable

          val accessorExpr =
            if setterExists then
              val setterSym = tpe.typeSymbol.methodMember(setterName).headOption
                .orElse(tpe.baseClasses.flatMap(_.methodMember(setterName)).headOption)
              setterSym match {
                case Some(sym) =>
                  val setterExpr = '{ (t: T, vv: v) => ${ Select('{ t }.asTerm, sym).appliedTo('{ vv }.asTerm).asExprOf[Unit] } }
                  '{ PropertyAccessor($getterExpr, $setterExpr) }
                case None =>
                  '{ PropertyAccessor.readOnly($getterExpr) }
              }
            else
              '{ PropertyAccessor.readOnly($getterExpr) }

          '{
            PropertyDescriptor(
              name = $nameExpr,
              propertyType = $propertyTypeExpr,
              annotations = $annotationsExpr,
              isWriteable = $isWriteableExpr,
              isReadable = $isReadableExpr,
              isPublic = $isPublicExpr,
              isPrivate = $isPrivateExpr,
              isProtected = $isProtectedExpr,
              accessor = Some($accessorExpr.asInstanceOf[PropertyAccessor[Any, Any]])
            )
          }
      }
    }

    '{ Array(${ Varargs(propertyDescriptors) }*) }
  }
  
  private def reflectTypeImpl[T: Type](using Quotes): Expr[TypeDescriptor] = {
    import quotes.reflect.*
    
    val tpe = TypeRepr.of[T]
    buildTypeDescriptor(tpe)
  }
  
  private def buildTypeDescriptor(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[TypeDescriptor] = {
    import quotes.reflect.*
    
    val normalized = tpe.widenTermRefByName.dealias
    
    normalized match {
      case AppliedType(raw, args) =>
        val rawClassExpr = reflectImplUsingType(raw)
        val argsExprs = args.map(buildTypeDescriptor)
        '{
          ParameterizedTypeDescriptor(
            rawType = $rawClassExpr,
            typeArguments = ${ Expr.ofList(argsExprs) }.toArray
          )
        }
      
      case tr: TypeRef if tr.typeSymbol.flags.is(Flags.Param) =>
        val nameExpr = Expr(tr.typeSymbol.name)
        val boundsExpr = extractBounds(tr.typeSymbol)
        '{ TypeVariableDescriptor(name = $nameExpr, bounds = $boundsExpr) }
      
      case arr if arr <:< TypeRepr.of[Array[?]] =>
        arr.asType match {
          case '[Array[a]] =>
            val componentExpr = buildTypeDescriptor(TypeRepr.of[a])
            '{ ArrayTypeDescriptor(componentType = $componentExpr) }
        }
      
      case _ =>
        reflectImplUsingType(normalized)
    }
  }
  
  private def reflectImplUsingType(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[ClassDescriptor] = {
    import quotes.reflect.*
    tpe.asType match {
      case '[t] => reflectImpl[t]
    }
  }

  private[macros] def extractPropertiesImpl[T: Type](using Quotes): Expr[Array[PropertyDescriptor]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val propertySymbols = collectPropertySymbols(tpe)

    val propertyDescriptors = propertySymbols.map { s =>
      val nameExpr = Expr(s.name)
      val propertyType = normalizeType(tpe.memberType(s))
      val propertyTypeExpr = buildTypeDescriptorSimple(propertyType)
      val annotationsExpr = extractAnnotations(s)

      val isWriteable = tpe.typeSymbol.methodMember(s"${s.name}_=").nonEmpty ||
        tpe.baseClasses.exists(_.methodMember(s"${s.name}_=").nonEmpty)
      val isWriteableExpr = Expr(isWriteable)

      val isPublicExpr = Expr(!s.flags.is(Flags.Private) && !s.flags.is(Flags.Protected))
      val isPrivateExpr = Expr(s.flags.is(Flags.Private))
      val isProtectedExpr = Expr(s.flags.is(Flags.Protected))
      val isReadableExpr = Expr(!s.flags.is(Flags.Private))

      '{
        PropertyDescriptor(
          name = $nameExpr,
          propertyType = $propertyTypeExpr,
          annotations = $annotationsExpr,
          isWriteable = $isWriteableExpr,
          isReadable = $isReadableExpr,
          isPublic = $isPublicExpr,
          isPrivate = $isPrivateExpr,
          isProtected = $isProtectedExpr,
          accessor = None
        )
      }
    }

    '{ Array(${ Varargs(propertyDescriptors) }*) }
  }

  private[macros] def buildTypeDescriptorSimple(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[TypeDescriptor] = {
    import quotes.reflect.*

    val normalized = tpe.widenTermRefByName.dealias

    normalized match {
      case tr: TypeRef if tr.typeSymbol.flags.is(Flags.Param) =>
        val nameExpr = Expr(tr.typeSymbol.name)
        '{ TypeVariableDescriptor(name = $nameExpr, bounds = Array.empty[String]) }

      case AppliedType(raw, args) =>
        val fullName = raw.typeSymbol.fullName
        val fullNameExpr = Expr(fullName)
        val simpleNameExpr = Expr(raw.typeSymbol.name)
        val annotationsExpr = extractAnnotations(raw.typeSymbol)
        val baseTypesExpr = extractBaseTypes(raw)
        val typeParamsExpr = extractTypeParameters(raw.typeSymbol)
        val isAbstractExpr = Expr(raw.typeSymbol.flags.is(Flags.Abstract))
        val isFinalExpr = Expr(raw.typeSymbol.flags.is(Flags.Final))
        val isCaseClassExpr = Expr(raw.typeSymbol.flags.is(Flags.Case))
        val typeArgumentsExprs = args.map(arg => buildTypeDescriptorSimple(arg))

        '{
          ParameterizedTypeDescriptor(
            rawType = ClassDescriptor(
              typeName = $fullNameExpr,
              simpleName = $simpleNameExpr,
              annotations = $annotationsExpr,
              properties = Array.empty[PropertyDescriptor],
              baseTypes = $baseTypesExpr,
              typeParameters = $typeParamsExpr,
              constructors = Array.empty[ConstructorDescriptor],
              isAbstract = $isAbstractExpr,
              isFinal = $isFinalExpr,
              isCaseClass = $isCaseClassExpr
            ),
            typeArguments = ${ Expr.ofList(typeArgumentsExprs.toList) }.toArray
          )
        }

      case _ =>
        val fullName = normalized.typeSymbol.fullName
        val fullNameExpr = Expr(fullName)
        val simpleNameExpr = Expr(normalized.typeSymbol.name)
        val annotationsExpr = extractAnnotations(normalized.typeSymbol)
        val baseTypesExpr = extractBaseTypes(normalized)
        val typeParamsExpr = extractTypeParameters(normalized.typeSymbol)
        val isAbstractExpr = Expr(normalized.typeSymbol.flags.is(Flags.Abstract))
        val isFinalExpr = Expr(normalized.typeSymbol.flags.is(Flags.Final))
        val isCaseClassExpr = Expr(normalized.typeSymbol.flags.is(Flags.Case))

        '{
          ClassDescriptor(
            typeName = $fullNameExpr,
            simpleName = $simpleNameExpr,
            annotations = $annotationsExpr,
            properties = Array.empty[PropertyDescriptor],
            baseTypes = $baseTypesExpr,
            typeParameters = $typeParamsExpr,
            constructors = Array.empty[ConstructorDescriptor],
            isAbstract = $isAbstractExpr,
            isFinal = $isFinalExpr,
            isCaseClass = $isCaseClassExpr
          )
        }
    }
  }

  private def reflectClassDescriptorOnly[T: Type](using Quotes): Expr[ClassDescriptor] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    val typeNameExpr = Expr(symbol.fullName)
    val simpleNameExpr = Expr(symbol.name)
    val annotationsExpr = extractAnnotations(symbol)
    val baseTypesExpr = extractBaseTypes(tpe)
    val typeParamsExpr = extractTypeParameters(symbol)
    val isAbstractExpr = Expr(symbol.flags.is(Flags.Abstract))
    val isFinalExpr = Expr(symbol.flags.is(Flags.Final))
    val isCaseClassExpr = Expr(symbol.flags.is(Flags.Case))

    '{
      ClassDescriptor(
        typeName = $typeNameExpr,
        simpleName = $simpleNameExpr,
        annotations = $annotationsExpr,
        properties = Array.empty[PropertyDescriptor],
        baseTypes = $baseTypesExpr,
        typeParameters = $typeParamsExpr,
        constructors = Array.empty[ConstructorDescriptor],
        isAbstract = $isAbstractExpr,
        isFinal = $isFinalExpr,
        isCaseClass = $isCaseClassExpr
      )
    }
  }

  private def extractPropertiesImplUsingType(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[Array[PropertyDescriptor]] = {
    import quotes.reflect.*

    val propertySymbols = collectPropertySymbols(tpe)

    val propertyDescriptors = propertySymbols.map { s =>
      val nameExpr = Expr(s.name)
      val propertyType = normalizeType(tpe.memberType(s))
      val propertyTypeExpr = buildTypeDescriptorSimple(propertyType)
      val annotationsExpr = extractAnnotations(s)

      val isWriteable = tpe.typeSymbol.methodMember(s"${s.name}_=").nonEmpty ||
        tpe.baseClasses.exists(_.methodMember(s"${s.name}_=").nonEmpty)
      val isWriteableExpr = Expr(isWriteable)

      val isPublicExpr = Expr(!s.flags.is(Flags.Private) && !s.flags.is(Flags.Protected))
      val isPrivateExpr = Expr(s.flags.is(Flags.Private))
      val isProtectedExpr = Expr(s.flags.is(Flags.Protected))
      val isReadableExpr = Expr(!s.flags.is(Flags.Private))

      '{
        PropertyDescriptor(
          name = $nameExpr,
          propertyType = $propertyTypeExpr,
          annotations = $annotationsExpr,
          isWriteable = $isWriteableExpr,
          isReadable = $isReadableExpr,
          isPublic = $isPublicExpr,
          isPrivate = $isPrivateExpr,
          isProtected = $isProtectedExpr,
          accessor = None
        )
      }
    }

    '{ Array(${ Varargs(propertyDescriptors) }*) }
  }
  
  private def extractConstructorsImpl[T: Type](using Quotes): Expr[Array[ConstructorDescriptor]] = {
    import quotes.reflect.*

    val symbol = TypeRepr.of[T].typeSymbol

    symbol.primaryConstructor match {
      case s if s == Symbol.noSymbol =>
        '{ Array.empty[ConstructorDescriptor] }

      case ctor =>
        val params = ctor.paramSymss.flatten
        val paramDescriptors = params.map { p =>
          val nameExpr = Expr(p.name)
          val paramType = normalizeType(TypeRepr.of[T].memberType(p))
          val paramTypeExpr = buildTypeDescriptorSimple(paramType)
          val annotationsExpr = extractAnnotations(p)
          val hasDefaultExpr = Expr(p.flags.is(Flags.HasDefault))

          '{
            ParameterDescriptor(
              name = $nameExpr,
              parameterType = $paramTypeExpr,
              annotations = $annotationsExpr,
              hasDefault = $hasDefaultExpr,
              defaultIndex = -1
            )
          }
        }

        val ctorAnnotationsExpr = extractAnnotations(ctor)
        val isPrimaryExpr = Expr(true)
        val isPrivateExpr = Expr(ctor.flags.is(Flags.Private))

        '{
          Array(
            ConstructorDescriptor(
              parameters = ${ Expr.ofList(paramDescriptors) }.toArray,
              annotations = $ctorAnnotationsExpr,
              isPrimary = $isPrimaryExpr,
              isPrivate = $isPrivateExpr
            )
          )
        }
    }
  }
  
  private[macros] def makeAccessorImpl[E: Type, V: Type](using Quotes)(selectorExpr: Expr[E => V]): Expr[PropertyAccessor[E, V]] = {
    import quotes.reflect.*

    val getterLambda = '{ (e: E) => $selectorExpr(e) }

    val selectedProperty = extractPropertySelector(selectorExpr)
    val setterName = s"${selectedProperty}_="
    val tpe = TypeRepr.of[E]
    
    val setterExists = tpe.typeSymbol.methodMember(setterName).nonEmpty ||
      tpe.baseClasses.exists(_.methodMember(setterName).nonEmpty)

    if setterExists then
      val setterSym = tpe.typeSymbol.methodMember(setterName).headOption
        .orElse(tpe.baseClasses.flatMap(_.methodMember(setterName)).headOption)
      
      setterSym match {
        case Some(sym) =>
          val setterLambda = '{
            (e: E, v: V) =>
              ${
                Select('{ e }.asTerm, sym).appliedTo('{ v }.asTerm).asExprOf[Unit]
              }
          }
          '{ PropertyAccessor($getterLambda, $setterLambda) }
        case None =>
          '{ PropertyAccessor.readOnly($getterLambda) }
      }
    else
      '{ PropertyAccessor.readOnly($getterLambda) }
  }
  
  private def createInstanceImpl[T: Type](using Quotes)(argsExpr: Expr[Seq[Any]]): Expr[T] = {
    import quotes.reflect.*

    val symbol = TypeRepr.of[T].typeSymbol

    if symbol.flags.is(Flags.Abstract) then
      report.errorAndAbort(s"Cannot instantiate abstract class ${symbol.fullName}")

    if symbol.flags.is(Flags.Case) then
      val applyMethod = symbol.companionClass.methodMember("apply").headOption
      applyMethod match {
        case Some(apply) =>
          val companion = Ref(symbol.companionModule)
          argsExpr match {
            case Varargs(argExprs) =>
              val argsList = argExprs.map(_.asTerm).toList
              val applyCall = Select(companion, apply).appliedToArgs(argsList)
              applyCall.asExprOf[T]
            case _ =>
              report.errorAndAbort(s"Expected varargs arguments for case class instantiation")
          }

        case None =>
          report.errorAndAbort(s"No apply method found in companion object of ${symbol.name}")
      }
    else
      report.errorAndAbort(s"Instantiation for non-case class ${symbol.name} requires explicit constructor invocation")
  }

  private def buildDefaultFactoryExpr[T: Type](using Quotes): Option[Expr[() => Any]] = {
    import quotes.reflect.*

    val symbol = TypeRepr.of[T].typeSymbol
    val constructor = symbol.primaryConstructor

    if symbol.flags.is(Flags.Abstract) || constructor == Symbol.noSymbol then
      None
    else
      val parameters = constructor.paramSymss.flatten
      if parameters.nonEmpty && !parameters.forall(_.flags.is(Flags.HasDefault)) then
        None
      else
        buildConstructorInvocation[T].map { invocation =>
          '{ (() => ${ invocation.asExprOf[T] }.asInstanceOf[Any]).asInstanceOf[() => Any] }
        }
  }

  private def buildConstructorInvocation[T: Type](using Quotes): Option[quotes.reflect.Term] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol
    val constructor = symbol.primaryConstructor

    if constructor == Symbol.noSymbol then
      None
    else
      val parameters = constructor.paramSymss.flatten
      val defaultArguments =
        parameters.zipWithIndex.map { case (_, index) =>
          val getterName = s"$$lessinit$$greater$$default$$${index + 1}"
          symbol.companionModule.methodMember(getterName).headOption.map(getter => Select(Ref(symbol.companionModule), getter))
        }

      if parameters.nonEmpty && defaultArguments.exists(_.isEmpty) then
        None
      else
        val newTerm = New(TypeTree.of[T])
        val ctorTerm = Select(newTerm, constructor)
        Some(Apply(ctorTerm, defaultArguments.flatten))
  }
  
  private[macros] def collectPropertySymbols(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.Symbol] = {
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
          val methodType = normalizeType(tpe.memberType(s))
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
  
  private[macros] def extractAnnotations(using Quotes)(symbol: quotes.reflect.Symbol): Expr[Array[Annotation]] = {
    import quotes.reflect.*

    val owner =
      try symbol.owner
      catch {
        case _: AssertionError => Symbol.noSymbol
      }

    val annotationOwnerCandidates =
      if owner == Symbol.noSymbol then
        List(symbol)
      else
        (symbol :: owner.declarations.filter(candidate => candidate.name == symbol.name).toList)
          .distinct

    val annotationSource =
      annotationOwnerCandidates.find(_.annotations.nonEmpty).getOrElse(symbol)

    val annotationExprs = annotationSource.annotations.flatMap { ann =>
      val className = ann.tpe.typeSymbol.fullName
      val params = extractAnnotationParams(ann)
      
      if params.isEmpty then
        Some('{ Annotation(${ Expr(className) }, Map.empty) })
      else
        val paramsExpr = '{ Map(${ Varargs(params.map { case (k, v) => '{ (${ Expr(k) }, $v) } }) }*) }
        Some('{ Annotation(${ Expr(className) }, $paramsExpr) })
    }
    
    '{ Array(${ Varargs(annotationExprs) }*) }
  }
  
  private[macros] def extractAnnotationParams(using Quotes)(ann: quotes.reflect.Term): List[(String, Expr[Any])] = {
    import quotes.reflect.*
    
    ann match {
      case Apply(_, args) =>
        val paramNames = ann.tpe.typeSymbol.primaryConstructor.paramSymss.flatten.map(_.name)
        args.zip(paramNames).collect {
          case (NamedArg(name, Literal(StringConstant(v))), _) => (name, Expr(v).asExprOf[Any])
          case (NamedArg(name, Literal(IntConstant(v))), _) => (name, Expr(v).asExprOf[Any])
          case (NamedArg(name, Literal(LongConstant(v))), _) => (name, Expr(v).asExprOf[Any])
          case (NamedArg(name, Literal(DoubleConstant(v))), _) => (name, Expr(v).asExprOf[Any])
          case (NamedArg(name, Literal(BooleanConstant(v))), _) => (name, Expr(v).asExprOf[Any])
          case (Literal(StringConstant(v)), name) => (name, Expr(v).asExprOf[Any])
          case (Literal(IntConstant(v)), name) => (name, Expr(v).asExprOf[Any])
          case (Literal(LongConstant(v)), name) => (name, Expr(v).asExprOf[Any])
          case (Literal(DoubleConstant(v)), name) => (name, Expr(v).asExprOf[Any])
          case (Literal(BooleanConstant(v)), name) => (name, Expr(v).asExprOf[Any])
        }
      case _ => Nil
    }
  }
  
  private[macros] def extractBaseTypes(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[Array[String]] = {
    import quotes.reflect.*
    
    val baseTypes = tpe.baseClasses
      .map(_.fullName)
      .filter(_.nonEmpty)
      .filter(_ != tpe.typeSymbol.fullName)
    
    '{ Array(${ Varargs(baseTypes.map(Expr(_))) }*) }
  }
  
  private[macros] def extractTypeParameters(using Quotes)(symbol: quotes.reflect.Symbol): Expr[Array[TypeDescriptor]] = {
    import quotes.reflect.*
    
    val typeParams = symbol.typeMembers.collect {
      case s if s.flags.is(Flags.Param) =>
        val nameExpr = Expr(s.name)
        val boundsExpr = extractBounds(s)
        '{ TypeVariableDescriptor(name = $nameExpr, bounds = $boundsExpr).asInstanceOf[TypeDescriptor] }
    }
    '{ Array(${ Varargs(typeParams) }*) }
  }
  
  private def extractBounds(using Quotes)(symbol: quotes.reflect.Symbol): Expr[Array[String]] = {
    import quotes.reflect.*
    
    val bounds = symbol.tree match {
      case tree: quotes.reflect.TypeDef =>
        tree.rhs match {
          case TypeBoundsTree(lo, hi) =>
            List(lo.tpe.show, hi.tpe.show).filter(_ != "Nothing").toArray
          case _ => Array.empty[String]
        }
      case _ => Array.empty[String]
    }
    Expr(bounds)
  }
  
  private[macros] def extractPropertySelector(using Quotes)(expr: Expr[Any]): String = {
    import quotes.reflect.*

    def loop(term: Term): String = term match {
      case Inlined(_, _, inner) => loop(inner)
      case Block(_, inner) => loop(inner)
      case Typed(inner, _) => loop(inner)
      case Select(qualifier, name) =>
        qualifier match {
          case Ident(paramName) => name
          case _ => loop(qualifier)
        }
      case Ident(name) => name
      case Lambda(params, body) => loop(body)
      case Closure(inner, _) => loop(inner)
      case Apply(TypeApply(Select(_, "apply"), _), List(inner)) => loop(inner)
      case _ => report.errorAndAbort(s"Expected a simple property selector: ${term.show}")
    }

    loop(expr.asTerm)
  }
  
  private[macros] def normalizeType(using Quotes)(tpe: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    
    @scala.annotation.tailrec
    def loop(current: TypeRepr): TypeRepr = {
      val simplified = current.widenTermRefByName.dealias.simplified
      simplified match {
        case AnnotatedType(inner, _) => loop(inner)
        case ByNameType(inner) => loop(inner)
        case mt: MethodType if mt.paramTypes.isEmpty => loop(mt.resType)
        case pt: PolyType => loop(pt.resType)
        case ct: ConstantType =>
          val widened = ct.widen
          if widened =:= ct then ct else loop(widened)
        case other => other
      }
    }
    
    loop(tpe)
  }
}
