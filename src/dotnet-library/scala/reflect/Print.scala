/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$


package scala.reflect

object Print extends Function1[Any, String] {

  def apply (any: Any): String =
    if (any.isInstanceOf[Code[Any]])
      apply(any.asInstanceOf[Code[Any]])
    else if (any.isInstanceOf[Tree])
      apply(any.asInstanceOf[Tree])
    else if (any.isInstanceOf[Symbol])
      apply(any.asInstanceOf[Symbol])
    else if (any.isInstanceOf[Type])
      apply(any.asInstanceOf[Type])
    else "UnknownAny"

  def apply(code: Code[Any]): String =
    Print(code.tree)

  def apply(tree: Tree): String = tree match {
    case reflect.Ident(sym) => Print(sym)
    case reflect.Select(qual, sym) => Print(qual) + "." + Print(sym)
    case reflect.Literal(value) => value match {
      case s:String => "\"" + s + "\""
      case _        => value.toString
    }
    case reflect.Apply(fun, args) =>
      Print(fun) + args.map(Print).mkString("(", ", ", ")")
    case reflect.TypeApply(fun, args) =>
      Print(fun) + args.map(Print).mkString("[", ", ", "]")
    case reflect.Function(params, body) =>
      params.map(Print).mkString("(", ", ", ")") + " => " + Print(body)
    case reflect.This(sym) => Print(sym)
    case reflect.Block(stats, expr) =>
      (stats ::: List(expr)).map(Print).mkString("{\n", ";\n", "\n}")
    case reflect.New(tpt) => "new " + Print(tpt)
    case reflect.If(condition, trueCase, falseCase) =>
      "if (" + Print(condition) + ") " + Print(trueCase) + " else " + Print(falseCase)
    case reflect.Assign(destination: Tree, source: Tree) =>
      Print(destination) + " = " + Print(source)
    case reflect.Target(sym, body) =>
      "target " + Print(sym) + " {\n" + Print(body) + "\n}"
    case reflect.Goto(target) =>
      "goto " + Print(target)
    case _ => "???"
  }

  def apply(symbol: Symbol): String = symbol match {
    case reflect.Class(name) => name.substring(name.lastIndexOf('.')+1)
    case reflect.Method(name, datatype) =>
      name.substring(name.lastIndexOf('.')+1) //+ ": " + datatype
    case reflect.Field(name, datatype) =>
      name.substring(name.lastIndexOf('.')+1) //+ ": " + datatype
    case reflect.TypeField(name, datatype) =>
      name.substring(name.lastIndexOf('.')+1) //+ ": " + datatype
    case reflect.LocalValue(owner, name, datatype) =>
      name.substring(name.lastIndexOf('.')+1) //+ ": " + datatype
    case reflect.LocalMethod(owner, name, datatype) =>
      name.substring(name.lastIndexOf('.')+1) //+ ": " + datatype
    case reflect.NoSymbol => "NoSymbol"
    case reflect.RootSymbol => "RootSymbol"
    case reflect.LabelSymbol(name) => name
    case _ => "???"
  }

  def apply(datatype: Type): String = datatype match {
    case reflect.NoPrefix => "NoPrefix"
    case reflect.NoType => "NoType"
    case reflect.NamedType(name) => "(named: " + name + ")"
    case reflect.PrefixedType(prefix, symbol) =>
      "(" + Print(prefix) + "." + Print(symbol) + ")"
    case reflect.SingleType(prefix, symbol) =>
      "(" + Print(prefix) + "." + Print(symbol) + ")"
    case reflect.ThisType(clazz) => "(" + Print(clazz) + ".this.type)"
    case reflect.AppliedType(datatype, args) =>
      Print(datatype) + args.map(Print).mkString("[", ", ", "]")
    case reflect.TypeBounds(lo, hi) =>
      "[" + Print(lo) + " ... " + Print(hi) + "]"
    case reflect.MethodType(formals, resultType) =>
      formals.map(Print).mkString("(", ", ", ")") + " => " + Print(resultType)
    case reflect.PolyType(typeParams, typeBounds, resultType) =>
      (List.map2(typeParams, typeBounds)
        ((tp, tb) => "[" + Print(tb._1) + " :> " + Print(tp) + " :> " + Print(tb._2) + "]")).
          mkString("[", ", ", "]") + " -> " + Print(resultType)
    case _ => "???"
  }

}