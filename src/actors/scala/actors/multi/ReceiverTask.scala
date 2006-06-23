/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$

package scala.actors.multi

/**
 * @author Philipp Haller
 */
class ReceiverTask(val actor: MailBox, msg: MailBox#Message) extends Runnable {
  def run(): Unit = {
    try {
      actor receiveMsg msg
    }
    catch {
      case d: Done =>
        // do nothing (continuation is already saved)
    }
  }
}