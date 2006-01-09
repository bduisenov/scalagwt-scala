/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2004, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
** $Id:Stream.scala 5359 2005-12-16 16:33:49 +0100 (Fri, 16 Dec 2005) dubochet $
\*                                                                      */

package scala;

/**
 * The object <code>Stream</code> provides helper functions
 * to manipulate streams.
 *
 * @author Martin Odersky, Matthias Zenger
 * @version 1.1 08/08/03
 */
object Stream {

  val empty: Stream[All] = new Stream[All] {
    def isEmpty = true;
    def head: All = error("head of empty stream");
    def tail: Stream[All] = error("tail of empty stream");
    def printElems(buf: StringBuffer, prefix: String): StringBuffer = buf;
  }

  def cons[a](hd: a, tl: => Stream[a]) = new Stream[a] {
    def isEmpty = false;
    def head = hd;
    private var tlVal: Stream[a] = _;
    private var tlDefined = false;
    def tail: Stream[a] = {
      if (!tlDefined) { tlVal = tl; tlDefined = true; }
      tlVal
    }
    def printElems(buf: StringBuffer, prefix: String): StringBuffer = {
      val buf1 = buf.append(prefix).append(hd);
      if (tlDefined) printElems(buf1, ", ") else buf1 append ", ?";
    }
  }

  def fromIterator[a](it: Iterator[a]): Stream[a] =
    if (it.hasNext) cons(it.next, fromIterator(it)) else empty;

  def concat[a](xs: Seq[Stream[a]]): Stream[a] = concat(xs.elements);

  def concat[a](xs: Iterator[Stream[a]]): Stream[a] = {
    if (xs.hasNext) xs.next append concat(xs)
    else empty;
  }

  /**
   * Create a stream with element values
   * <code>v<sub>n+1</sub> = v<sub>n</sub> + 1</code>
   * where <code>v<sub>0</sub> = start</code>
   * and <code>v<sub>i</sub> &lt; end</code>.
   *
   * @param start the start value of the stream
   * @param end the end value of the stream
   * @return the stream starting at value <code>start</code>.
   */
  def range(start: Int, end: Int): Stream[Int] =
    range(start, end, 1);

  /**
   * Create a stream with element values
   * <code>v<sub>n+1</sub> = v<sub>n</sub> + step</code>
   * where <code>v<sub>0</sub> = start</code>
   * and <code>v<sub>i</sub> &lt; end</code>.
   *
   * @param start the start value of the stream
   * @param end the end value of the stream
   * @param step the increment value of the stream
   * @return the stream starting at value <code>start</code>.
   */
  def range(start: Int, end: Int, step: Int): Stream[Int] = {
    def loop(lo: Int): Stream[Int] =
      if (lo >= end) empty
      else cons(lo, loop(lo + step));
    loop(start)
  }

  /**
   * Create a stream with element values
   * <code>v<sub>n+1</sub> = step(v<sub>n</sub>)</code>
   * where <code>v<sub>0</sub> = start</code>
   * and <code>v<sub>i</sub> &lt; end</code>.
   *
   * @param start the start value of the stream
   * @param end the end value of the stream
   * @param step the increment function of the stream
   * @return the stream starting at value <code>start</code>.
   */
  def range(start: Int, end: Int, step: Int => Int): Stream[Int] = {
    def loop(lo: Int): Stream[Int] =
      if (lo >= end) empty
      else cons(lo, loop(step(lo)));
    loop(start)
  }
}

/**
 * <p>The class <code>Stream</code> implements lazy lists where elements
 * are only evaluated when they are needed. Here is an example:</p>
 * <pre>
 * <b>object</b> Main <b>with</b> Application {
 *
 *   <b>def</b> from(n: Int): Stream[Int] =
 *     Stream.cons(n, from(n + 1));
 *
 *   <b>def</b> sieve(s: Stream[Int]): Stream[Int] =
 *     Stream.cons(s.head, sieve(s.tail filter { x => x % s.head != 0 }));
 *
 *   <b>def</b> primes = sieve(from(2));
 *
 *   primes take 10 print
 * }
 * </pre>
 *
 * @author Martin Odersky, Matthias Zenger
 * @version 1.1 08/08/03
 */
trait Stream[+a] extends Seq[a] {

  def isEmpty: Boolean;
  def head: a;
  def tail: Stream[a];

  def length: int = if (isEmpty) 0 else tail.length + 1;

  def append[b >: a](rest: => Stream[b]): Stream[b] =
    if (isEmpty) rest
    else Stream.cons(head, tail.append(rest));

  def elements: Iterator[a] = new Iterator[a] {
    var current = Stream.this;
    def hasNext: boolean = !current.isEmpty;
    def next: a = { val result = current.head; current = current.tail; result }
  }

  def init: Stream[a] =
    if (isEmpty) error("Stream.empty.init")
    else if (tail.isEmpty) Stream.empty
    else Stream.cons(head, tail.init);

  def last: a =
    if (isEmpty) error("Stream.empty.last")
    else if (tail.isEmpty) head
    else tail.last;

  override def take(n: int): Stream[a] =
    if (n == 0) Stream.empty
    else Stream.cons(head, tail.take(n-1));

  override def drop(n: int): Stream[a] =
    if (n == 0) this
    else tail.drop(n-1);

  def apply(n: int) = drop(n).head;
  def at(n: int) = drop(n).head;

  def takeWhile(p: a => Boolean): Stream[a] =
    if (isEmpty || !p(head)) Stream.empty
    else Stream.cons(head, tail.takeWhile(p));

  def dropWhile(p: a => Boolean): Stream[a] =
    if (isEmpty || !p(head)) this
    else tail.dropWhile(p);

  def map[b](f: a => b): Stream[b] =
    if (isEmpty) Stream.empty
    else Stream.cons(f(head), tail.map(f));

  override def foreach(f: a => unit): unit =
    if (isEmpty) {}
    else { f(head); tail.foreach(f) }

  def filter(p: a => Boolean): Stream[a] =
    if (isEmpty) this
    else if (p(head)) Stream.cons(head, tail.filter(p))
    else tail.filter(p);

  override def forall(p: a => Boolean): Boolean =
    isEmpty || (p(head) && tail.forall(p));

  override def exists(p: a => Boolean): Boolean =
    !isEmpty && (p(head) || tail.exists(p));

  override def foldLeft[b](z: b)(f: (b, a) => b): b =
    if (isEmpty) z
    else tail.foldLeft[b](f(z, head))(f);

  override def foldRight[b](z: b)(f: (a, b) => b): b =
    if (isEmpty) z
    else f(head, tail.foldRight(z)(f));

  def reduceLeft[b >: a](f: (b, b) => b): b =
    if (isEmpty) error("Stream.empty.reduceLeft")
    else ((tail: Stream[b]) foldLeft (head: b))(f);

  def reduceRight[b >: a](f: (b, b) => b): b =
    if (isEmpty) error("Stream.empty.reduceRight")
    else if (tail.isEmpty) head: b
    else f(head, tail.reduceRight(f));

  def flatMap[b](f: a => Stream[b]): Stream[b] =
    if (isEmpty) Stream.empty
    else f(head).append(tail.flatMap(f));

  def reverse: Stream[a] =
    foldLeft(Stream.empty: Stream[a])((xs, x) => Stream.cons(x, xs));

  // The following method is not compilable without run-time type
  // information. It should therefore be left commented-out for
  // now.
  //       def toArray: Array[a] = {
  //         val xs = new Array[a](length);
  //         copyToArray(xs, 0);
  //         xs
  //       }

  override def copyToArray[b >: a](xs: Array[b], start: int): Array[b] =
    if (isEmpty) xs
    else { xs(start) = head; tail.copyToArray(xs, start + 1) }

  def zip[b](that: Stream[b]): Stream[Tuple2[a, b]] =
    if (this.isEmpty || that.isEmpty) Stream.empty
    else Stream.cons(Tuple2(this.head, that.head), this.tail.zip(that.tail));

  def print: unit =
    if (isEmpty) Console.println("Stream.empty")
    else {
      Console.print(head);
      Console.print(", ");
      tail.print
    }

  override def toString() =
    "Stream(" + printElems(new StringBuffer(), "") + ")";

  def printElems(buf: StringBuffer, prefix: String): StringBuffer;
}