/* NSC -- new Scala compiler
 * Copyright 2005-2010 LAMP/EPFL
 * @author  Paul Phillips
 */

package scala.tools.nsc
package backend

import io.AbstractFile
import util.JavaClassPath
import util.ClassPath.{ JavaContext, DefaultJavaContext }
import scala.tools.util.PathResolver

trait JavaSrcPlatform extends Platform[AbstractFile] {
  import global._
  import definitions.{ BoxesRunTimeClass, getMember }

  lazy val classPath  = new PathResolver(settings).result
  def rootLoader      = new loaders.JavaPackageLoader(classPath)

  def platformPhases = List(
    flatten,    // get rid of inner classes
    genJava      // generate .class files
  )

  lazy val externalEquals = getMember(BoxesRunTimeClass, nme.equals_)
  def externalEqualsNumNum = getMember(BoxesRunTimeClass, "equalsNumNum")
  def externalEqualsNumChar = getMember(BoxesRunTimeClass, "equalsNumChar")
  def externalEqualsNumObject = getMember(BoxesRunTimeClass, "equalsNumObject")

  def isMaybeBoxed(sym: Symbol): Boolean = {
    import definitions._
    (sym == ObjectClass) ||
    (sym == SerializableClass) ||
    (sym == ComparableClass) ||
    (sym isNonBottomSubClass BoxedNumberClass) ||
    (sym isNonBottomSubClass BoxedCharacterClass)
  }
}
