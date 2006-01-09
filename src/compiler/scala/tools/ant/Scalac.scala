/*    __  ______________                                                      *\
**   /  |/ / ____/ ____/                                                      **
**  / | | /___  / /___                                                        **
** /_/|__/_____/_____/ Copyright 2005-2006 LAMP/EPFL                          **
**
** $Id$
\*                                                                            */

package scala.tools.ant {

  import java.io.File
  import java.net.{URL, URLClassLoader}
  import java.util.{ArrayList, Vector}

  import org.apache.tools.ant.{AntClassLoader, BuildException,
                               DirectoryScanner, Project}
  import org.apache.tools.ant.taskdefs.MatchingTask
  import org.apache.tools.ant.types.Path
  import org.apache.tools.ant.util.{FileUtils, GlobPatternMapper,
                                    SourceFileScanner}
  import org.apache.tools.ant.types.{EnumeratedAttribute, Reference}

  import scala.tools.nsc.reporters.{Reporter, ConsoleReporter}
  import scala.tools.nsc.{Global, FatalError, Settings}

  /** An Ant task to compile with the new Scala compiler (NSC).
    * This task can take the following parameters as attributes:<ul>
    *  <li>srcdir (mandatory),</li>
    *  <li>srcref,</li>
    *  <li>destdir,</li>
    *  <li>classpath,</li>
    *  <li>classpathref,</li>
    *  <li>sourcepath,</li>
    *  <li>sourcepathref,</li>
    *  <li>bootclasspath,</li>
    *  <li>bootclasspathref,</li>
    *  <li>extdirs,</li>
    *  <li>extdirsref,</li>
    *  <li>encoding,</li>
    *  <li>force,</li>
    *  <li>logging,</li>
    *  <li>logphase,</li>
    *  <li>usepredefs,</li>
    *  <li>debugcode.</li>
    * </ul>
    * It also takes the following parameters as nested elements:<ul>
    *  <li>src (for srcdir),</li>
    *  <li>classpath,</li>
    *  <li>sourcepath,</li>
    *  <li>bootclasspath,</li>
    *  <li>extdirs.</li>
    * </ul>
    *
    * @author Gilles Dubochet */
  class Scalac extends MatchingTask {

    private val SCALA_PRODUCT: String =
      System.getProperty("scala.product", "scalac")
    private val SCALA_VERSION: String =
      System.getProperty("scala.version", "Unknown version")

    /** The unique Ant file utilities instance to use in this task. */
    private val fileUtils = FileUtils.newFileUtils()

/******************************************************************************\
**                             Ant user-properties                            **
\******************************************************************************/

    abstract class PermissibleValue {
      val values: List[String]
      def isPermissible(value: String): Boolean =
        (value == "") || values.exists(.startsWith(value))
    }

    /** Defines valid values for the logging property. */
    object LoggingLevel extends PermissibleValue {
      val values = List("none", "verbose", "debug")
    }

    /** Defines valid values for properties that refer to compiler phases. */
    object CompilerPhase extends PermissibleValue {
      val values = List("namer", "typer", "pickler", "uncurry", "tailcalls",
                        "transmatch", "explicitouter", "erasure", "lambdalift",
                        "flatten", "constructors", "mixin", "icode", "jvm",
                        "terminal")
    }

    /** The directories that contain source files to compile. */
    private var origin: Option[Path] = None
    /** The directory to put the compiled files in. */
    private var destination: Option[File] = None

    /** The class path to use for this compilation. */
    private var classpath: Option[Path] = None
    /** The source path to use for this compilation. */
    private var sourcepath: Option[Path] = None
    /** The boot class path to use for this compilation. */
    private var bootclasspath: Option[Path] = None
    /** The external extensions path to use for this compilation. */
    private var extdirs: Option[Path] = None

    /** The character encoding of the files to compile. */
    private var encoding: Option[String] = None

    /** Whether to force compilation of all files or not. */
    private var force: Boolean = false
    /** How much logging output to print. Either none (default),
      * verbose or debug. */
    private var logging: Option[String] = None
    /** Which compilation phases should be logged during compilation. */
    private var logPhase: List[String] = Nil

    /** Whether to use implicit predefined values or not. */
    private var usepredefs: Boolean = true;
    /** Instruct the compiler to generate debugging information */
    private var debugCode: Boolean = false

/******************************************************************************\
**                             Properties setters                             **
\******************************************************************************/

    /** Sets the srcdir attribute. Used by Ant.
      * @param input The value of <code>origin</code>. */
    def setSrcdir(input: Path) =
      if (origin.isEmpty) origin = Some(input)
      else origin.get.append(input)

    /** Sets the <code>origin</code> as a nested src Ant parameter.
      * @return An origin path to be configured. */
    def createSrc(): Path = {
      if (origin.isEmpty) origin = Some(new Path(getProject()))
      origin.get.createPath()
    }

    /** Sets the <code>origin</code> as an external reference Ant parameter.
      * @param input A reference to an origin path. */
    def setSrcref(input: Reference) =
      createSrc().setRefid(input)

    /** Sets the destdir attribute. Used by Ant.
      * @param input The value of <code>destination</code>. */
    def setDestdir(input: File) =
      destination = Some(input)

    /** Sets the classpath attribute. Used by Ant.
      * @param input The value of <code>classpath</code>. */
    def setClasspath(input: Path) =
      if (classpath.isEmpty) classpath = Some(input)
      else classpath.get.append(input)

    /** Sets the <code>classpath</code> as a nested classpath Ant parameter.
      * @return A class path to be configured. */
    def createClasspath(): Path = {
      if (classpath.isEmpty) classpath = Some(new Path(getProject()))
      classpath.get.createPath()
    }

    /** Sets the <code>classpath</code> as an external reference Ant parameter.
      * @param input A reference to a class path. */
    def setClasspathref(input: Reference) =
      createClasspath().setRefid(input)

    /** Sets the sourcepath attribute. Used by Ant.
      * @param input The value of <code>sourcepath</code>. */
    def setSourcepath(input: Path) =
      if (sourcepath.isEmpty) sourcepath = Some(input)
      else sourcepath.get.append(input)

    /** Sets the <code>sourcepath</code> as a nested sourcepath Ant parameter.
      * @return A source path to be configured. */
    def createSourcepath(): Path = {
      if (sourcepath.isEmpty) sourcepath = Some(new Path(getProject()))
      sourcepath.get.createPath()
    }

    /** Sets the <code>sourcepath</code> as an external reference Ant parameter.
      * @param input A reference to a source path. */
    def setSourcepathref(input: Reference) =
      createSourcepath().setRefid(input)

    /** Sets the boot classpath attribute. Used by Ant.
      * @param input The value of <code>bootclasspath</code>. */
    def setBootclasspath(input: Path) =
      if (bootclasspath.isEmpty) bootclasspath = Some(input)
      else bootclasspath.get.append(input)

    /** Sets the <code>bootclasspath</code> as a nested sourcepath Ant
      * parameter.
      * @return A source path to be configured. */
    def createBootclasspath(): Path = {
      if (bootclasspath.isEmpty) bootclasspath = Some(new Path(getProject()))
      bootclasspath.get.createPath()
    }

    /** Sets the <code>bootclasspath</code> as an external reference Ant
      * parameter.
      * @param input A reference to a source path. */
    def setBootclasspathref(input: Reference) =
      createBootclasspath().setRefid(input)

    /** Sets the external extensions path attribute. Used by Ant.
      * @param input The value of <code>extdirs</code>. */
    def setExtdirs(input: Path) =
      if (extdirs.isEmpty) extdirs = Some(input)
      else extdirs.get.append(input)

    /** Sets the <code>extdirs</code> as a nested sourcepath Ant parameter.
      * @return An extensions path to be configured. */
    def createExtdirs(): Path = {
      if (extdirs.isEmpty) extdirs = Some(new Path(getProject()))
      extdirs.get.createPath()
    }

    /** Sets the <code>extdirs</code> as an external reference Ant parameter.
      * @param input A reference to an extensions path. */
    def setExtdirsref(input: Reference) =
      createExtdirs().setRefid(input)

    /** Sets the encoding attribute. Used by Ant.
      * @param input The value of <code>encoding</code>. */
    def setEncoding(input: String): Unit =
      encoding = Some(input)

    /** Sets the force attribute. Used by Ant.
      * @param input The value for <code>force</code>. */
    def setForce(input: Boolean): Unit =
      force = input

    /** Sets the logging level attribute. Used by Ant.
      * @param input The value for <code>logging</code>. */
    def setLogging(input: String) =
      if (LoggingLevel.isPermissible(input)) logging = Some(input)
      else error("Logging level '" + input + "' does not exist.")

    /** Sets the log attribute. Used by Ant.
      * @param input The value for <code>logPhase</code>. */
    def setLogPhase(input: String) = {
      logPhase = List.fromArray(input.split(",")).flatMap(s: String => {
        val st = s.trim()
        if (CompilerPhase.isPermissible(st))
          (if (input != "") List(st) else Nil)
        else {
          error("Phase " + st + " in log does not exist.")
          Nil
        }
      })
    }

    /** Sets the use predefs attribute. Used by Ant.
      * @param input The value for <code>usepredefs</code>. */
    def setUsepredefs(input: Boolean): Unit =
      usepredefs = input;

    /** Set the debug info attribute. */
    def setDebugCode(input: Boolean): Unit =
      debugCode = input

/******************************************************************************\
**                             Properties getters                             **
\******************************************************************************/

    /** Gets the value of the classpath attribute in a Scala-friendly form.
      * @returns The class path as a list of files. */
    private def getClasspath: List[File] =
      if (classpath.isEmpty) error("Member 'classpath' is empty.")
      else
        List.fromArray(classpath.get.list()).map(nameToFile)

    /** Gets the value of the origin attribute in a Scala-friendly form.
      * @returns The origin path as a list of files. */
    private def getOrigin: List[File] =
      if (origin.isEmpty) error("Member 'origin' is empty.")
      else List.fromArray(origin.get.list()).map(nameToFile)

    /** Gets the value of the destination attribute in a Scala-friendly form.
      * @returns The destination as a file. */
    private def getDestination: File =
      if (destination.isEmpty) error("Member 'destination' is empty.")
      else existing(getProject().resolveFile(destination.get.toString()))

    /** Gets the value of the sourcepath attribute in a Scala-friendly form.
      * @returns The source path as a list of files. */
    private def getSourcepath: List[File] =
      if (sourcepath.isEmpty) error("Member 'sourcepath' is empty.")
      else List.fromArray(sourcepath.get.list()).map(nameToFile)

    /** Gets the value of the bootclasspath attribute in a Scala-friendly form.
      * @returns The boot class path as a list of files. */
    private def getBootclasspath: List[File] =
    if (bootclasspath.isEmpty) error("Member 'bootclasspath' is empty.")
    else List.fromArray(bootclasspath.get.list()).map(nameToFile)

    /** Gets the value of the extdirs attribute in a Scala-friendly form.
      * @returns The extensions path as a list of files. */
    private def getExtdirs: List[File] =
      if (extdirs.isEmpty) error("Member 'extdirs' is empty.")
      else List.fromArray(extdirs.get.list()).map(nameToFile)

/******************************************************************************\
**                       Compilation and support methods                      **
\******************************************************************************/

    /** This is forwarding method to circumvent bug #281 in Scala 2. Remove when
      * bug has been corrected. */
    override protected def getDirectoryScanner(baseDir: java.io.File) =
      super.getDirectoryScanner(baseDir)

    /** Transforms a string name into a file relative to the provided base
      * directory.
      * @param base A file pointing to the location relative to which the name
      *   will be resolved.
      * @param name A relative or absolute path to the file as a string.
      * @return A file created from the name and the base file. */
    private def nameToFile(base: File)(name: String): File =
      existing(fileUtils.resolveFile(base, name))

    /** Transforms a string name into a file relative to the build root
      * directory.
      * @param name A relative or absolute path to the file as a string.
      * @return A file created from the name. */
    private def nameToFile(name: String): File =
      existing(getProject().resolveFile(name))

    /** Tests if a file exists and prints a warning in case it doesn't. Always
      * returns the file, even if it doesn't exist.
      * @param file A file to test for existance.
      * @return The same file. */
    private def existing(file: File): File = {
      if (!file.exists())
        log("Element '" + file.toString() + "' does not exist.",
            Project.MSG_WARN)
      file
    }

    /** Transforms a path into a Scalac-readable string.
      * @param path A path to convert.
      * @return A string-representation of the path like 'a.jar:b.jar'. */
    private def asString(path: List[File]): String =
      path.map(asString).mkString("", File.pathSeparator, "")

    /** Transforms a file into a Scalac-readable string.
      * @param path A file to convert.
      * @return A string-representation of the file like '/x/k/a.scala'. */
    private def asString(file: File): String =
      file.getAbsolutePath()

    /** Generates a build error. Error location will be the current task in the
      * ant file.
      * @param message A message describing the error.
      * @throws BuildException A build error exception thrown in every case. */
    private def error(message: String): All =
      throw new BuildException(message, getLocation())

/******************************************************************************\
**                           The big execute method                           **
\******************************************************************************/

    /** Performs the compilation. */
    override def execute() = {

      // Tests if all mandatory attributes are set and valid.
      if (origin.isEmpty) error("Attribute 'srcdir' is not set.")
      if (getOrigin.isEmpty) error("Attribute 'srcdir' is not set.")
      if (!destination.isEmpty && !destination.get.isDirectory())
        error("Attribute 'destdir' does not refer to an existing directory.")
      if (destination.isEmpty) destination = Some(getOrigin.head)

      val mapper = new GlobPatternMapper()
      mapper.setTo("*.class")
      mapper.setFrom("*.scala")

      // Scans source directories to build up a compile lists.
      // If force is false, only files were the .class file in destination is
      // older than the .scala file will be used.
      val sourceFiles: List[File] =
        for (val originDir <- getOrigin;
             val originFile <- {
              var includedFiles =
                getDirectoryScanner(originDir).getIncludedFiles()
              if (!force) {
                includedFiles = new SourceFileScanner(this).
                  restrict(includedFiles, originDir, destination.get, mapper)
              }
              (List.fromArray(includedFiles)).
                map(nameToFile(originDir))
            })
        yield {
          log(originFile.toString(), Project.MSG_DEBUG)
          originFile
        }

      if (sourceFiles.length == 0)
        log("No files selected for compilation", Project.MSG_VERBOSE)
      else log("Compiling " + sourceFiles.length + " source file" +
               (if (sourceFiles.length > 1) "s" else "") +
               (" to " + getDestination.toString()))

      // Builds-up the compilation settings for Scalac with the existing Ant
      // parameters.
      val reporter = new ConsoleReporter()
      val settings = new Settings(error)
      settings.outdir.value = asString(destination.get)
      if (!classpath.isEmpty)
        settings.classpath.value = asString(getClasspath)
      if (!sourcepath.isEmpty)
        settings.sourcepath.value = asString(getSourcepath)
      else if (origin.get.size() > 0)
        settings.sourcepath.value = origin.get.list()(0)
      if (!bootclasspath.isEmpty)
        settings.bootclasspath.value = asString(getBootclasspath)
      if (!extdirs.isEmpty) settings.extdirs.value = asString(getExtdirs)
      if (!encoding.isEmpty) settings.encoding.value = encoding.get
      if (!logging.isEmpty && logging.get == "verbose")
        settings.verbose.value = true
      else if (!logging.isEmpty && logging.get == "debug") {
        settings.verbose.value = true
        settings.debug.value = true
      }
      if (!logPhase.isEmpty) settings.log.value = logPhase
      settings.nopredefs.value = !usepredefs;
      settings.debuginfo.value = debugCode

      // Compiles the actual code
      val compiler = new Global(settings, reporter)
      try {
        (new compiler.Run).compile(sourceFiles.map(f:File=>f.toString()))
        if (reporter.errors > 0)
          error("Compile failed with " +
                reporter.errors + " error" +
                (if (reporter.errors > 1) "s" else "") +
                "; see the compiler error output for details."
               )
      }
      catch {
        case exception @ FatalError(msg) => {
          exception.printStackTrace()
          if (settings.debug.value) exception.printStackTrace()
          error("Compile failed because of an internal compiler error (" + msg +
                "); see the error output for details.")
        }
      }
      if (reporter.warnings > 0)
        log("Compile suceeded with " +
            reporter.warnings + " warning" +
            (if (reporter.warnings > 1) "s" else "") +
            "; see the compiler output for details."
           )
      reporter.printSummary()
    }

  }

}