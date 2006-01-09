/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003, LAMP/EPFL                  **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
** $Id$
\*                                                                      */

package scala.collection.mutable;


/** This is a simple wrapper class for <code>scala.collection.mutable.Map</code>.
 *  It is most useful for assembling customized map abstractions
 *  dynamically using object composition and forwarding.
 *
 *  @author  Matthias Zenger
 *  @version 1.0, 21/07/2003
 */
trait MapProxy[A, B] extends Map[A, B] with scala.collection.MapProxy[A, B] {

    def self: Map[A, B];

    def update(key: A, value: B): Unit = self.update(key, value);

    override def ++=(map: Iterable[Pair[A, B]]): Unit = self ++= map;

    override def ++=(it: Iterator[Pair[A, B]]): Unit = self ++= it;

    override def incl(mappings: Pair[A, B]*): Unit = self ++= mappings;

    def -=(key: A): Unit = self -= key;

    override def --=(keys: Iterable[A]): Unit = self --= keys;

    override def --=(it: Iterator[A]): Unit = self --= it;

    override def excl(keys: A*): Unit = self --= keys;

    override def clear: Unit = self.clear;

    override def map(f: (A, B) => B): Unit = self.map(f);

    override def filter(p: (A, B) => Boolean): Unit = self.filter(p);

    override def toString() = self.toString();

    override def mappingToString(p: Pair[A, B]) = self.mappingToString(p);

    override def <<(cmd: Message[Pair[A, B]]): Unit = self << cmd;

    override def clone(): Map[A, B] = new MapProxy[A, B] { def self = MapProxy.this.self.clone() }
}