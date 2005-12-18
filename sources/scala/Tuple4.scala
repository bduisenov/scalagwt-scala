/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002, LAMP/EPFL                  **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package scala;

case class Tuple4[+T1, +T2, +T3, +T4](_1: T1, _2: T2, _3: T3, _4: T4) {
  override def toString(): String = "(" + _1 + "," + _2 + "," + _3 + "," + _4 + ")";
}