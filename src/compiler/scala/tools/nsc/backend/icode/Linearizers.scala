/* NSC -- new scala compiler
 * Copyright 2005 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id$

package scala.tools.nsc.backend.icode;

import scala.tools.nsc.ast._;
import scala.collection.mutable.Stack;

mixin class Linearizers requires ICodes {
  import opcodes._;

  trait Linearizer {
    def linearize(c: IMethod): List[BasicBlock];
  }

  /**
   * A simple linearizer which predicts all branches to
   * take the 'success' branch and tries to schedule those
   * blocks immediately after the test. This is in sync with
   * how 'while' statements are translated (if the test is
   * 'true', the loop continues).
   */
  class NormalLinearizer extends Linearizer with WorklistAlgorithm {
    type Elem = BasicBlock;
    type WList = Stack[Elem];

    val worklist: WList = new Stack();

    var blocks: List[BasicBlock] = Nil;

    def linearize(m: IMethod): List[BasicBlock] = {
      val b = m.code.startBlock;
      blocks = Nil;

      run {
        worklist ++= (m.exh map (.startBlock));
        worklist.push(b);
      }

      blocks.reverse;
    }

    /** Linearize another subtree and append it to the existing blocks. */
    def linearize(startBlock: BasicBlock): List[BasicBlock] = {
      //blocks = startBlock :: Nil;
      run( { worklist.push(startBlock); } );
      blocks.reverse;
    }

    def processElement(b: BasicBlock) =
      if (b.size > 0) {
        add(b);
        b.lastInstruction match {
          case JUMP(where) =>
            add(where);
          case CJUMP(success, failure, _, _) =>
            add(success);
            add(failure);
          case CZJUMP(success, failure, _, _) =>
            add(success);
            add(failure);
          case SWITCH(_, labels) =>
            add(labels);
          case RETURN(_) => ();
          case THROW() =>   ();
        }
      }

    def dequeue: Elem = worklist.pop;

    /**
     * Prepend b to the list, if not already scheduled.
     * TODO: use better test than linear search
     */
    def add(b: BasicBlock) =
      if (blocks.contains(b))
        ()
      else {
        blocks = b :: blocks;
        worklist push b;
      }

    def add(bs: List[BasicBlock]): Unit = bs foreach add;
  }
}