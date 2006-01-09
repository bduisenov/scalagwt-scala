/* NSC -- new scala compiler
 * Copyright 2005 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id$

package scala.tools.nsc.backend.icode;

import scala.collection.mutable.{Buffer, ListBuffer, Map, HashMap};
import scala.tools.nsc.symtab._;

abstract class Checkers {
  val global: Global;
  import global._;

  /**
   * This class performs a set of checks similar to what the bytecode
   * verifier does. For each basic block, it checks that:
   *
   * - for primitive operations: the type and numer of operands match
   *   the type of the operation
   *
   * - for method calls: the method exists in the type of the receiver
   *   and the number and type of arguments match the declared type of
   *   the method.
   *
   * - for object creation: the constructor can be called.
   *
   * - for load/stores: the field/local/param exists and the type
   *   of the value matches that of the target.
   *
   * For a control flow graph it checks that type stacks at entry to
   * each basic block 'agree':
   *
   * - they have the same length
   * - there exists a lub for all types at the same position in stacks.
   *
   * TODO: Better checks for MONITOR_ENTER/EXIT
   *       Better checks for local var initializations
   */
  class ICodeChecker {
    import icodes._;
    import opcodes._;

    var clasz: IClass = _;
    var method: IMethod = _;
    var code: Code = _;

    val in: Map[BasicBlock, TypeStack] = new HashMap();
    val out: Map[BasicBlock, TypeStack] = new HashMap();

    val emptyStack = new TypeStack();

    val STRING = REFERENCE(definitions.StringClass);
    val SCALA_ALL = REFERENCE(definitions.AllClass);
    val SCALA_ALL_REF = REFERENCE(definitions.AllRefClass);
    val CASE_CLASS = REFERENCE(definitions.getClass("scala.CaseClass"));

    def checkICodes: Unit = {
      Console.println("[[consistency check at beginning of phase " + globalPhase.name + "]]");
      classes foreach check;
    }

    def check(cls: IClass): Unit = {
      log("Checking class " + cls);
      clasz = cls;

      for (val f1 <- cls.fields; val f2 <- cls.fields; f1 ne f2)
        if (f1.symbol.name == f2.symbol.name)
          Checkers.this.global.error("Repetitive field name: " +
                                     f1.symbol.fullNameString);

      for (val m1 <- cls.methods; val m2 <- cls.methods; m1 ne m2)
        if (m1.symbol.name == m2.symbol.name &&
            m1.symbol.tpe =:= m2.symbol.tpe)
          Checkers.this.global.error("Repetitive method: " +
                                     m1.symbol.fullNameString);
      clasz.methods.foreach(check);
    }

    /** Apply the give funtion to each pair of the cartesian product of
     * l1 x l2.
     */
    def pairwise[a](l1: List[a], l2: List[a])(f: (a, a) => Unit) =
      l1 foreach { x =>
        l2 foreach { y => f(x, y) }
      }

    def check(m: IMethod): Unit = {
      log("Checking method " + m);
      method = m;
      if (!m.isDeferred)
        check(m.code);
    }

    def check(c: Code): Unit = {
      var worklist: Buffer[BasicBlock] = new ListBuffer();

      def append(elems: List[BasicBlock]) = elems foreach appendBlock;
      def appendBlock(bl: BasicBlock) =
        if ( !worklist.exists(bl.==) )
          worklist + bl;

      in.clear;  out.clear;
      code = c;
      worklist + c.startBlock;
      c.blocks foreach ( bl => { in  += bl -> emptyStack;
                                 out += bl -> emptyStack } );

      while (worklist.length > 0) {
        val block = worklist(0); worklist.trimStart(1);
        val output = check(block, in(block));
        if (output != out(block) ||
            (out(block) eq emptyStack)) {
          log("Output changed for block: " + block.fullString);
          out(block) = output;
          append(block.successors);
          block.successors foreach meet;
        }
      }
    }

    /**
     * Apply the meet operator of the stack lattice on bl's predecessors.
     * :-). Compute the input to bl by checking that all stacks have the
     * same length, and taking the lub of types at the same positions.
     */
    def meet(bl: BasicBlock): Unit = {
      val preds = bl.predecessors;

      def meet2(s1: TypeStack, s2: TypeStack): TypeStack = {
        if (s1 eq emptyStack) s2
        else if (s2 eq emptyStack) s1
        else {
          if (s1.length != s2.length)
            throw new CheckerError("Incompatible stacks: " + s1 + " and " + s2 + " in " + method + " at entry to block: " + bl);
          new TypeStack(List.map2(s1.types, s2.types) (lub))
        }
      }

      if (preds != Nil) {
        in(bl) = (preds map out.apply) reduceLeft meet2;
        log("Input changed for block: " + bl +" to: " + in(bl));
      }
    }


    private var typeStack: TypeStack = null;
    private var instruction: Instruction = null;
    private var basicBlock: BasicBlock = null;

    /**
     * Check the basic block to be type correct and return the
     * produced type stack.
     */
    def check(b: BasicBlock, initial: TypeStack): TypeStack = {
      log("** Checking block:\n" + b.fullString + " with initial stack:\n" + initial);
      var stack = new TypeStack(initial);

      this.typeStack = stack;
      this.basicBlock = b;

      def typeError(k1: TypeKind, k2: TypeKind): Unit =
        error(" expected: " + k1 + " but " + k2 + " found");

      b traverse (instr => {

        def checkStack(len: Int) =
          if (stack.length < len)
            ICodeChecker.this.error("Expected at least " + len + " elements on the stack", stack);
          else
            ();

        def checkLocal(local: Local) =
          method.lookupLocal(local.sym.name) match {
            case None => error(" " + local + " is not defined in method " + method);
            case _ => ()
          }

        def checkField(obj: TypeKind, field: Symbol) =
          obj match {
            case REFERENCE(sym) =>
              if (sym.info.member(field.name) == NoSymbol)
                error(" " + field + " is not defined in class " + clasz);
            case _ =>
              error(" expected reference type, but " + obj + " found");
          }

        /** Checks that tpe is a subtype of one of the allowed types */
        def checkType(tpe: TypeKind, allowed: TypeKind*) =
          if (isOneOf(tpe, allowed: _*))
            ()
          else
            error(tpe.toString() + " is not one of: " + allowed);

        /** Checks that the 2 topmost elements on stack are of the
         *  kind TypeKind.
         */
        def checkBinop(kind: TypeKind) = {
          val Pair(a, b) = stack.pop2;
          checkType(a, kind);
          checkType(b, kind);
        }

        /** Check that arguments on the stack match method params. */
        def checkMethodArgs(method: Symbol) = {
          val params = method.info.paramTypes;
          checkStack(params.length);
          params.reverse.foreach( (tpe) => checkType(stack.pop, toTypeKind(tpe)));
        }

        /** Checks that the object passed as receiver has a method
         *  'method' and that it is callable from the current method.
         */
        def checkMethod(receiver: TypeKind, method: Symbol) =
          receiver match {
            case REFERENCE(sym) =>
              checkBool(sym.info.member(method.name) != NoSymbol,
                        "Method " + method + " does not exist in " + sym.fullNameString);
              if (method hasFlag Flags.PRIVATE)
                checkBool(method.owner == clasz.symbol,
                          "Cannot call private method of " + method.owner.fullNameString
                          + " from " + clasz.symbol.fullNameString);
              else if (method hasFlag Flags.PROTECTED)
                checkBool(clasz.symbol isSubClass method.owner,
                          "Cannot call protected method of " + method.owner.fullNameString
                          + " from " + clasz.symbol.fullNameString);

            case ARRAY(_) =>
              checkBool(receiver.toType.member(method.name) != NoSymbol,
                        "Method " + method + " does not exist in " + receiver);

            case t =>
              error("Not a reference type: " + t);
          }

        def checkBool(cond: Boolean, msg: String) =
          if (cond) () else error(msg);

        this.instruction = instr;

        if (settings.debug.value) {
          log("PC: " + instr);
          log("stack: " + stack);
          log("================");
        }
        instr match {
          case THIS(clasz) =>
            stack push toTypeKind(clasz.tpe);

          case CONSTANT(const) =>
            stack push toTypeKind(const.tpe);

          case LOAD_ARRAY_ITEM(kind) =>
            checkStack(2);
            stack.pop2 match {
              case Pair(INT, ARRAY(elem)) =>
                if (!(elem <:< kind))
                  typeError(kind, elem);
                stack.push(elem);
              case Pair(a, b) =>
                error(" expected and INT and a array reference, but " +
                    a + ", " + b + " found");
            }

         case LOAD_LOCAL(local, isArg) =>
           checkLocal(local);
           stack.push(local.kind);

         case LOAD_FIELD(field, isStatic) =>
           if (isStatic) {
             // the symbol's owner should contain it's field, but
             // this is already checked by the type checker, no need
             // to redo that here
           } else {
             checkStack(1);
             val obj = stack.pop;
             checkField(obj, field);
           }
           stack.push(toTypeKind(field.tpe));

         case LOAD_MODULE(module) =>
           checkBool((module.isModule || module.isModuleClass),
                     "Expected module: " + module + " flags: " + Flags.flagsToString(module.flags));
           stack.push(toTypeKind(module.tpe));

         case STORE_ARRAY_ITEM(kind) =>
           checkStack(3);
           stack.pop3 match {
             case Triple(k, INT, ARRAY(elem)) =>
                if (!(k <:< kind))
                  typeError(kind, k);
                if (!(k <:< elem))
                  typeError(elem, k);
             case Triple(a, b, c) =>
                error(" expected and array reference, and int and " + kind +
                      " but " + a + ", " + b + ", " + c + " found");
           }

         case STORE_LOCAL(local, isArg) =>
           checkLocal(local);
           checkStack(1);

           val actualType = stack.pop;
           if (!(actualType <:< local.kind) &&
               actualType != CASE_CLASS &&
               local.kind != SCALA_ALL_REF)
             typeError(local.kind, actualType);

         case STORE_FIELD(field, isStatic) =>
           if (isStatic) {
             checkStack(1);
             val fieldType = toTypeKind(field.tpe);
             val actualType = stack.pop;
             if (!(actualType <:< fieldType))
               typeError(fieldType, actualType);
           } else {
             checkStack(2);
             stack.pop2 match {
               case Pair(value, obj) =>
                 checkField(obj, field);
               val fieldType = toTypeKind(field.tpe);
               if (fieldType != SCALA_ALL_REF && !(value <:< fieldType))
               typeError(fieldType, value);
             }
           }

         case CALL_PRIMITIVE(primitive) =>
           checkStack(instr.consumed);
           primitive match {
             case Negation(kind) =>
               checkType(kind, BOOL, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);
               checkType(stack.pop, kind);
               stack push kind;

             case Test(op, kind, zero) =>
               if (zero) {
                 val actualType = stack.pop;
                 checkType(actualType, kind);
               } else
                 checkBinop(kind);
               stack push BOOL;

             case Comparison(op, kind) =>
               checkType(kind, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);
               checkBinop(kind);
               stack push INT;

             case Arithmetic(op, kind) =>
               checkType(kind, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);
               if (op == NOT)
                 checkType(stack.pop, kind)
               else
                 checkBinop(kind);
               stack push kind;

             case Logical(op, kind) =>
               checkType(kind,  BOOL, BYTE, CHAR, SHORT, INT, LONG);
               checkBinop(kind);
               stack push kind;

             case Shift(op, kind) =>
               checkType(kind, BYTE, CHAR, SHORT, INT, LONG);
               val Pair(a, b) = stack.pop2;
               checkType(a, INT);
               checkType(b, kind);
               stack push kind;

             case Conversion(src, dst) =>
               checkType(src, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);
               checkType(dst, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);
               checkType(stack.pop, src);
               stack push dst;

             case ArrayLength(kind) =>
               val arr = stack.pop;
               arr match {
                 case ARRAY(elem) =>
                   checkType(elem, kind);
                 case _ =>
                   error(" array reference expected, but " + arr + " found");
               }
               stack push INT;

             case StartConcat =>
               stack.push(ConcatClass);

             case EndConcat =>
               checkType(stack.pop, ConcatClass);
               stack.push(STRING);

             case StringConcat(el) =>
               checkType(stack.pop, el);
               checkType(stack.pop, ConcatClass);
               stack push ConcatClass;
           }

         case CALL_METHOD(method, style) =>
           style match {
             case Dynamic =>
               checkStack(1 + method.info.paramTypes.length);
               checkMethodArgs(method);
               checkMethod(stack.pop, method);
               stack.push(toTypeKind(method.info.resultType));

             case Static(onInstance) =>
               if (onInstance) {
                 checkStack(1 + method.info.paramTypes.length);
                 checkBool(method.hasFlag(Flags.PRIVATE) || method.isConstructor,
                           "Static call to non-private method.");
                 checkMethodArgs(method);
                 checkMethod(stack.pop, method);
                 if (!method.isConstructor)
                   stack.push(toTypeKind(method.info.resultType));
               } else {
                 checkStack(method.info.paramTypes.length);
                 checkMethodArgs(method);
                 stack.push(toTypeKind(method.info.resultType));
               }

             case SuperCall(mix) =>
               checkStack(1 + method.info.paramTypes.length);
               checkMethodArgs(method);
               checkMethod(stack.pop, method);
               stack.push(toTypeKind(method.info.resultType));

           }

          case NEW(kind) =>
            kind match {
              case REFERENCE(cls) =>
                stack.push(kind);

              case _ => error("NEW call to non-reference type: " + kind);
            }

          case CREATE_ARRAY(elem) =>
            checkStack(1);
            checkType(stack.pop, INT);
            stack.push(ARRAY(elem));

          case IS_INSTANCE(tpe) =>
            val ref = stack.pop;
            checkBool(ref.isReferenceType || ref.isArrayType,
                      "IS_INSTANCE on primitive type: " + ref);
            checkBool(tpe.isReferenceType || tpe.isArrayType,
                      "IS_INSTANCE to primitive type: " + tpe);
            stack.push(BOOL);

          case CHECK_CAST(tpe) =>
            val ref = stack.pop;
            checkBool(ref.isReferenceType || ref.isArrayType,
                      "CHECK_CAST on primitive type: " + ref);
            checkBool(tpe.isReferenceType || tpe.isArrayType,
                      "CHECK_CAST to primitive type: " + tpe);
            stack.push(tpe);

          case SWITCH(tags, labels) =>
            checkType(stack.pop, INT);
            checkBool(tags.length == labels.length - 1,
                      "The number of tags and labels does not coincide.");
            checkBool(labels forall (b => code.blocks contains b),
                      "Switch target cannot be found in code.");

          case JUMP(where) =>
            checkBool(code.blocks contains where,
                      "Jump to non-existant block " + where);

          case CJUMP(success, failure, cond, kind) =>
            checkBool(code.blocks contains success,
                      "Jump to non-existant block " + success);
            checkBool(code.blocks contains failure,
                      "Jump to non-existant block " + failure);
            checkBinop(kind);

          case CZJUMP(success, failure, cond, kind) =>
            checkBool(code.blocks contains success,
                      "Jump to non-existant block " + success);
            checkBool(code.blocks contains failure,
                      "Jump to non-existant block " + failure);
            checkType(stack.pop, kind);

          case RETURN(kind) =>
            kind match {
              case UNIT => ();

              case REFERENCE(_) | ARRAY(_) =>
                checkStack(1);
                val top = stack.pop;
                checkBool(top.isReferenceType || top.isArrayType,
                            "" + kind + " is a reference type, but " + top + " is not");
              case _ =>
                checkStack(1);
                val top = stack.pop;
                checkType(top, kind);
            }

          case THROW() =>
            val thrown = stack.pop;
            checkBool(thrown.toType <:< definitions.ThrowableClass.tpe,
                      "Element on top of stack should implement 'Throwable': " + thrown);
            stack.push(SCALA_ALL);

          case DROP(kind) =>
            checkType(stack.pop, kind);

          case DUP(kind) =>
            val top = stack.pop;
            checkType(top, kind);
            stack.push(top);
            stack.push(top);

          case MONITOR_ENTER() =>
            checkStack(1);
            checkBool(stack.pop.isReferenceType,
                      "MONITOR_ENTER on non-reference type");

          case MONITOR_EXIT() =>
            checkStack(1);
            checkBool(stack.pop.isReferenceType,
                      "MONITOR_EXIT on non-reference type");

          case _ => abort("Unknown instruction: " + instr);
        }
      });
      stack
    }

    //////////////// Error reporting /////////////////////////

    def error(msg: String): Unit = {
      System.out.println(method.toString() + " in block: " + basicBlock.label);
      printLastIntructions;

      Checkers.this.global.error("ICode checker: " + method + ": " + msg);
    }

    /** Prints the last 4 instructions. */
    def printLastIntructions = {
      var printed = 0;
      var buf: List[Instruction] = Nil;

      basicBlock.traverseBackwards( (i) =>
        if (i == instruction || (printed > 0 && printed < 3)) {
          buf = i :: buf;
          printed = printed + 1;
        });
      buf foreach System.out.println;
      Console.println("at: " + clasz.cunit.position(buf.head.pos));
    }

    def error(msg: String, stack: TypeStack): Unit = {
      error(msg + "\n type stack: " + stack);
    }


    //////////////////// Checking /////////////////////////////

    /** Return true if k1 is a subtype of any of the following types. */
    def isOneOf(k1: TypeKind, kinds: TypeKind*) =
      kinds.exists( k => k1 <:< k);


    /**
     * Dummy TypeKind to represent the ConcatClass in a platform-independent
     * way. For JVM it would have been a REFERENCE to 'StringBuffer'.
     */
    case object ConcatClass extends TypeKind {
      override def toString() = "ConcatClass";

      /**
       * Approximate `lub'. The common type of two references is
       * always AnyRef. For 'real' least upper bound wrt to subclassing
       * use method 'lub'.
       */
      override def maxType(other: TypeKind): TypeKind =
        other match {
          case REFERENCE(_) => REFERENCE(definitions.AnyRefClass);
            case _ =>
              abort("Uncomparbale type kinds: ConcatClass with " + other);
        }

      /** Checks subtyping relationship. */
      override def <:<(other: TypeKind): Boolean = (this eq other);

      override def isReferenceType: Boolean = false;
    }
  }
}