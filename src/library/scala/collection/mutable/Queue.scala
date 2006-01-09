/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003, LAMP/EPFL                  **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
** $Id$
\*                                                                      */

package scala.collection.mutable;


/** <code>Queue</code> objects implement data structures that allow to
 *  insert and retrieve elements in a first-in-first-out (FIFO) manner.
 *
 *  @author  Matthias Zenger
 *  @version 1.1, 03/05/2004
 */
[serializable, cloneable]
class Queue[A] extends MutableList[A] {

    /** Checks if the queue is empty.
     *
     *  @return true, iff there is no element in the queue.
     */
    def isEmpty: Boolean = (first == null);

    /** Inserts a single element at the end of the queue.
     *
     *  @param  elem        the element to insert
     */
    def +=(elem: A): Unit = appendElem(elem);

    /** Adds all elements provided by an <code>Iterable</code> object
     *  at the end of the queue. The elements are prepended in the order they
     *  are given out by the iterator.
     *
     *  @param  iter        an iterable object
     */
    def ++=(iter: Iterable[A]): Unit = this ++= iter.elements;

    /** Adds all elements provided by an iterator
     *  at the end of the queue. The elements are prepended in the order they
     *  are given out by the iterator.
     *
     *  @param  it        an iterator
     */
    def ++=(it: Iterator[A]): Unit = it foreach appendElem;

    /** Adds all elements to the queue.
     *
     *  @param  elems       the elements to add.
     */
    def enqueue(elems: A*): Unit = (this ++= elems);

    /** Returns the first element in the queue, and removes this element
     *  from the queue.
     *
     *  @return the first element of the queue.
     */
    def dequeue: A = {
        if (first == null)
            error("queue empty");
        else {
            val res = first.elem;
            first = first.next;
            if (first == null)
                last = null;
            len = len - 1;
            res;
        }
    }

    /** Returns the first element in the queue which satisfies the
     *  given predicate, and removes this element from the queue.
     *
     *  @param p   the predicate used for choosing the first element
     *  @return the first element of the queue for which p yields true
     */
    def dequeueFirst(p: A => Boolean): Option[A] = {
    	if (first == null)
			None
		else if (p(first.elem)) {
			val res: Option[A] = Some(first.elem);
			first = first.next;
			len = len - 1;
			if (first == null) {
				last = null;
			} else if (first.next == null) {
				last = first;
			}
			res
		} else
			extractFirst(first, p) match {
				case None => None
				case Some(cell) => Some(cell.elem)
			}
    }

    /** Returns all elements in the queue which satisfy the
     *  given predicate, and removes those elements from the queue.
     *
     *  @param p   the predicate used for choosing elements
     *  @return    a sequence of all elements in the queue for which
     *             p yields true.
     */
    def dequeueAll(p: A => Boolean): Seq[A] = {
    	val res = new ArrayBuffer[A];
 		if (first == null)
 			res;
 		else {
 			while (p(first.elem)) {
				res += first.elem;
				first = first.next;
				len = len - 1;
				if (first == null) {
					last = null;
				} else if (first.next == null) {
					last = first;
				}
			}
			var cell: Option[LinkedList[A]] = extractFirst(first, p);
			while (!cell.isEmpty) {
				res += cell.get.elem;
				cell = extractFirst(cell.get, p);
			}
			res
		}
    }

    private def extractFirst(start: LinkedList[A], p: A => Boolean): Option[LinkedList[A]] = {
		var cell = start;
		while ((cell.next != null) && !p(cell.next.elem)) {
			cell = cell.next;
		}
		if (cell.next == null)
			None
		else {
			val res: Option[LinkedList[A]] = Some(cell.next);
			cell.next = cell.next.next;
			if (cell.next == null)
				last = cell;
			len = len - 1;
			res
		}
	}

    /** Returns the first element in the queue, or throws an error if there
     *  is no element contained in the queue.
     *
     *  @return the first element.
     */
    def front: A = first.elem;

    /** Removes all elements from the queue. After this operation is completed,
     *  the queue will be empty.
     */
    def clear: Unit = reset;

    /** Checks if two queues are structurally identical.
     *
     *  @return true, iff both queues contain the same sequence of elements.
     */
    override def equals(that: Any): Boolean = (
        that.isInstanceOf[Queue[A]] &&
        { val other = that.asInstanceOf[Queue[A]];
          elements.zip(other.elements).forall {
            case Pair(thiselem, thatelem) => thiselem == thatelem;
        }}
    );

    /** The hashCode method always yields an error, since it is not
     *  safe to use mutable queues as keys in hash tables.
     *
     *  @return never.
     */
    override def hashCode(): Int = error("unsuitable as hash key");

    /** Returns a textual representation of a queue as a string.
     *
     *  @return the string representation of this queue.
     */
    override def toString() = toList.mkString("Queue(", ", ", ")");

    /** This method clones the queue.
     *
     *  @return  a queue with the same elements.
     */
    override def clone(): Queue[A] = {
        val res = new Queue[A];
        res ++= this;
        res
    }
}