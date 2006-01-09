/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2004, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
** $Id$
\*                                                                      */

package scala.testing;

/** unit testing methods in the spirit of JUnit framework.
 *  use these classes like this:
<code>
 import scala.testing.SUnit;
  import SUnit._;


  class MyTest(n:String) extends TestCase(n) {

    override def runTest() = n match {
      case "myTest1" =>   assertTrue( true );
      case "myTest2" =>   assertTrue( "hello", false );
    }
  }

  val r = new TestResult();
  suite.run(r);
  for(val tf &lt;- r.failures()) {
    Console.println(tf.toString())
  }
}
</code>
 */
object SUnit {

  /** a Test can be run with its result being collected */
  trait Test {
    def run(r: TestResult):Unit;
  }

  /** a TestCase defines the fixture to run multiple tests */
  class TestCase(val name: String) extends Test with Assert {
    protected def createResult() =
      new TestResult();

    protected def runTest(): Unit =
      {}

    def run(r: TestResult): Unit =
      try {
        runTest();
      } catch {
        case t:Throwable => r.addFailure(this, t);
      }

    def run(): Unit =
      run(createResult());

    def setUp() =
      {}

    def tearDown() =
      {}

    override def toString() =
      name;
  }

  /** a TestFailure collects a failed test together with the thrown exception */
  class TestFailure(val failedTest:Test, val thrownException:Throwable) {

    def this(p:Pair[Test,Throwable]) = this(p._1, p._2);

    override def toString() =
      failedTest.toString()+" failed due to "+thrownException.toString();

    def trace(): String = {
      val s = new StringBuffer();
      for(val trElem <- thrownException.getStackTrace()) {
        s.append(trElem.toString());
        s.append('\n');
      }
      s.toString()
    }
  }

  /** a TestResult collects the result of executing a test case */
  class TestResult {
    val buf =
      new scala.collection.mutable.ArrayBuffer[Pair[Test,Throwable]]();

    def addFailure(test:Test, t:Throwable) =
      buf += Pair(test,t);

    def failureCount() =
      buf.length;

    def failures() =
      buf.elements map { x => new TestFailure(x) };
  }

  /** a TestSuite runs a composite of test cases */
  class TestSuite(tests:Test*) extends Test {

    def this(names:Seq[String], constr:String=>Test) =
      this((names.toList map constr):_*);

    val buf =
      new scala.collection.mutable.ArrayBuffer[Test]();

    buf ++= tests;

    def addTest(t: Test) =
      buf += t;

    def run(r: TestResult):Unit = {
      for(val t <- buf) {
        t.run(r);
      }
    }
  }

  /** an AssertFailed is thrown for a failed assertion */
  case class AssertFailed(msg:String) extends java.lang.RuntimeException {
    override def toString() =
      "failed assertion:"+msg;
  }

  /** this trait defined useful assert methods */
  trait Assert {
    /** equality */
    def assertEquals[A](msg:String, expected:A, actual: => A): Unit =
      if( expected != actual ) fail(msg);

    /** equality */
    def assertEquals[A](expected:A, actual: => A): Unit  =
      assertEquals("(no message)", expected, actual);

    /** falseness */
    def assertFalse(msg:String, actual: => Boolean): Unit =
      assertEquals(msg, false, actual);
    /** falseness */
    def assertFalse(actual: => Boolean): Unit =
      assertFalse("(no message)", actual);

    /** not null */
    def assertNotNull(msg:String, actual: => AnyRef): Unit =
      if( null == actual ) fail(msg);

    /** not null */
    def assertNotNull(actual: => AnyRef): Unit  =
      assertNotNull("(no message)", actual);

    /** reference inequality */
    def assertNotSame(msg:String, expected: => AnyRef, actual: => AnyRef): Unit =
      if(expected.eq(actual)) fail(msg);
    /** reference inequality */
    def assertNotSame(expected: => AnyRef, actual: => AnyRef): Unit  =
      assertNotSame("(no message)", expected, actual);

    /** null */
    def assertNull(msg:String, actual: => AnyRef): Unit =
        if( null != actual ) fail(msg);
    /** null */
    def assertNull(actual: => AnyRef): Unit =
      assertNull("(no message)", actual);


    /** reference equality */
    def assertSame(msg:String, expected: => AnyRef, actual: => AnyRef): Unit =
        if(!expected.eq(actual)) fail(msg);
    /** reference equality */
    def assertSame(expected: => AnyRef, actual: => AnyRef): Unit  =
      assertSame("(no message)", expected, actual);

    /** trueness */
    def assertTrue(msg:String, actual: => Boolean): Unit =
      assertEquals(msg, true, actual);
    /** trueness */
    def assertTrue(actual: => Boolean): Unit  =
      assertTrue("(no message)", actual);

    /** throws AssertFailed with given message */
    def fail(msg:String): Unit =
      throw new AssertFailed(msg);
  }
}