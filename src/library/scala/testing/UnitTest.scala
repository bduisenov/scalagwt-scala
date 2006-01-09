/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2004, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
** $Id$
\*                                                                      */

package scala.testing;

/** some simple methods to support unit testing with assertions
**  to contain more JUnit style assertions which use Scala's features.
*/
object UnitTest {

  class Report( report_ok:()=>Unit, report_fail:(String,String)=>Unit ) {
    def ok:Unit = report_ok();
    def fail( actual:String, expected:String ):Unit =
      report_fail( actual, expected );
  }

  var report = new Report(
    { () => Console.println("passed ok") },
    { (actual:String, expected:String) =>
        Console.print("failed! we got");
         Console.print( "\""+ actual +"\"" );
         Console.println(" but expected \"" + expected + "\"") });

  def setReporter( r:Report ) = {
    this.report = r;
  }

  def assertSameElements[a]( actual:Seq[a] , expected: Seq[a] ):Unit =
    if( actual.sameElements( expected ) )
        report.ok
    else
	report.fail( actual.toString(), expected.toString() );

  def assertEquals[a]( actual: a, expected: a ):Unit =
    if( actual == expected )
        report.ok
    else
	report.fail( actual.toString(), expected.toString() );

  def assertTrue( actual: Boolean ):Unit = assertEquals(actual, true);
  def assertFalse( actual: Boolean ):Unit = assertEquals(actual, false);


  def assertNotEquals[a]( actual: a, expected: a ):Unit =
    if( actual != expected )
        report.ok
    else
	report.fail( actual.toString(), "x != "+expected.toString() );

  //def test[a]( def doit: a, expected: a ):Unit = assertEquals( doit, expected );

} // unitTest