/* NSC -- new scala compiler
 * Copyright 2005 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id$
package scala.tools.nsc;

import scala.tools.nsc.reporters.Reporter;

abstract class Interpreter {
  import scala.collection.mutable.ListBuffer;
  import symtab.Names;

  // list of names defined, for each line number
  val prevDefines : ListBuffer[Pair[Int,ListBuffer[Names#Name]]] = new ListBuffer();

  val compiler: Global;

  import scala.tools.nsc.ast.parser.SyntaxAnalyzer;
  object syntaxAnalyzer extends SyntaxAnalyzer {
    val global: compiler.type = compiler
  }

  def interpret(line: String, reporter: Reporter): unit = {
    import scala.tools.nsc.util.SourceFile;

    // convert input to a compilation unit, using SourceFile;
    // and parse it, using syntaxAnalyzer, to get input ASTs
    val inASTs = syntaxAnalyzer.interpreterParse(
                   new compiler.CompilationUnit(
                     //if SourceFile is not modified, then fix a bug
                     //here by adding an EOF character to the end of
                     //the 'line'
                     new SourceFile("<console>",line.toCharArray())));

    //todo:  if (errors in parsing) after reporting them, exit method

    val dvt = new DefinedVarsTraverser;
    dvt.traverseTrees(inASTs);
    val definedVars = dvt.definedVars;

    val ivt = new ImportVarsTraverser(definedVars);
    ivt.traverseTrees(inASTs);
    val importVars = ivt.importVars;

    val lineno = prevDefines.length;
    //todo: it is probably nice to include date & time, as well as a process id, in the filename
    val filename = getTempPath().getPath()+java.io.File.separator+"InterpreterTempLine"+lineno+".scala";
    writeTempScalaFile(filename, line, lineno, definedVars, importVars);

    // first phase: compile auto-generated file
    compiler.settings.outdir.value = getTempPath().getPath();
    val cr = new compiler.Run;
    cr compile List(filename);

    //todo: if no errors in compilation then
    // second phase: execute JVM, and print outcome
    // else consider definition as if has not happened and exit method
    //todo: use Scala's reflection API, which I designed, instead, for the following code
    val cl = new java.net.URLClassLoader(Predef.Array(getTempPath().toURL()));
    val interpreterResultObject: Class = Class.forName("InterpreterLine"+lineno+"Result",true,cl);
    val resultValMethod: java.lang.reflect.Method = interpreterResultObject.getMethod("result",null);
    var interpreterResultString: String = resultValMethod.invoke(interpreterResultObject,null).toString();

    //var interpreterResultJavaTypeString: String = resultValMethod.getReturnType().getName();
    //Console.println(compiler.definitions.EmptyPackage.info.members);
    val interpreterResultSym: compiler.Symbol =
        compiler.definitions.getMember(compiler.definitions.EmptyPackage,
          compiler.newTermName("InterpreterLine"+lineno+"Result"));

    def findSymbolWithName(ls: List[compiler.Symbol], name: compiler.Name): compiler.Symbol =
      ls.find(s=>s.name == name) match {
        case None => throw new IllegalStateException("Cannot find field '"+name+"' in InterpreterResult");
        case Some(s) => s;
      }

    //var lastname: String = compiler.atPhase(cr.typerPhase.next){interpreterResultSym.info.decls.toList.last.name.toString()};
    //reporter.info(null,lastname,true);
    //todo: similar to what I should be doing for Scala's reflection??
    var interpreterResultScalaTypeString: String =
      compiler.atPhase(cr.typerPhase.next){
        findSymbolWithName(interpreterResultSym.info.decls.toList,
          compiler.nme.getterToLocal(compiler.newTermName("result")))
        .tpe.toString()
      };
    reporter.info(null,interpreterResultString+": "+interpreterResultScalaTypeString/*+" ("+interpreterResultJavaTypeString+")"*/,true);

/*
    val scalaInterpFile: File = ScalaInterpFile(filename);
    scalaInterpFile.deleteOnExit();
    if(scalaInterpFile.exists())
       scalaInterpFile.delete();

    getvalue of line#.last_var_defined_in_line (from defined_vars)
    (works for 'it' as it was added as last val to definedvars)
    and send it to reporter
*/

    // book-keeping
    //todo: if no errors in evaluation then
    prevDefines += Pair(lineno,definedVars);
    // else consider definition as if has not happened.

    // report some debug info
    //reporter.info(null,"inASTs="+inASTs,true);
    //reporter.info(null,"definedVars="+definedVars,true);
    //reporter.info(null,"importVars="+importVars,true);
    //reporter.info(null,"prevDefines="+prevDefines,true);
  }

  import java.io.File;
  def getTempPath(): File = {
    val tempdir = {
      val tempdir1 = System.getProperty("java.io.tmpdir");
      if (tempdir1 == null){
        val tempdir2 = System.getProperty("TEMP");
        if (tempdir2 == null){
          val tempdir3 = System.getProperty("TMP");
          if (tempdir3 == null)
            throw new IllegalStateException("No temporary folder defined")
          else tempdir3 }
        else tempdir2 }
      else tempdir1
    };
    val path = new File(tempdir);
    if (!path.exists() || !path.isDirectory())
      throw new IllegalStateException("Invalid temporary directory")
    else if (!path.canWrite())
      throw new IllegalStateException("Temporary directory not writable")
    else path
  };

  def writeTempScalaFile(filename: String, line: String, lineno: Int, definedVars: ListBuffer[Names#Name], importVars: ListBuffer[Pair[Names#Name,Int]]) = {
    import java.io.{File, PrintWriter, FileOutputStream};
    val scalaFile = new File(filename);
    scalaFile.deleteOnExit();
    if(scalaFile.exists()) // to prevent old lingering files from having results from them reported!
       scalaFile.delete();

    val module = new PrintWriter(new FileOutputStream(scalaFile));
    //todo:"import "+LoadedModules?.getName
    //module.println("\n");

    for(val Pair(ivname,ivlineno) <- importVars.toList) yield
       module.println("import line"+ivlineno+"."+ivname+";\n");

    module.println("object line"+lineno+" {");
    var fullLine = line;
    if(definedVars.length == 0) { // input is just an expression
      fullLine = "  var it = " + line;
      definedVars += compiler.encode("it"); }
    else fullLine = "  " + line;
    module.println(fullLine);
    module.println("}");
    module.println();
    module.println("object InterpreterLine"+lineno+"Result ");
    module.println("{ val result = (line"+lineno+"."+definedVars.toList.reverse.head+"); }");
    // reflection is used later (see above) to get the result value above

    module.flush();
    module.close();
  }

  import compiler.Traverser;
  import compiler.Tree;
  class DefinedVarsTraverser extends Traverser {
    val definedVars = new ListBuffer[Names#Name];
    override def traverse(ast: Tree): unit =
      if (!ast.isDef) ()
      else {
        import compiler._;
        ast match {
          // only the outer level needed, so do not recurse to go deeper
          // todo: combine similar cases in one case
          case ClassDef(_,name,_,_,_) => definedVars += name
          case ModuleDef(_, name,_) => definedVars += name
          case ValDef(_, name,_,_) => definedVars += name
          case DefDef(_,name,_,_,_,_) => definedVars += name
          //todo:case Bind(name,_) => ((name != nme.WILDCARD) && (definedVars.elements forall (name !=))) definedVars += name

          //case Ident(name) => if (name...is defined) definedVars += name;

          //todo:
          //case PackageDef(name, _) => throw new InterpIllegalDefException(name.toString()+": package definitions not allowed")
          //case AbsTypeDef(_,name,_,_) => throw new InterpIllegalDefException(name.toString()+": absract type definitions not allowed")
          //case AliasTypeDef(_,name,_,_) => throw new InterpIllegalDefException(name.toString()+": alias type definitions not allowed")
          //case LabelDef(name,_,_) => throw new InterpIllegalDefException(name.toString()+": label definitions not allowed")
          case _ => throw new InterpIllegalDefException("Unsupported interpreter definition. Contact Scala developers for adding interpreter support for it.")// ()
        }
      }
  }
  case class InterpIllegalDefException(msg: String) extends RuntimeException(msg);

//    class ListTraverser extends Traverser {
//      def traverse(trees: List[Tree]): Unit =
//        trees foreach traverse;
//    }
//
//    class ListDefinedVarsTraverser extends DefinedVarsTraverser with ListTraverser;

  class ImportVarsTraverser(definedVars: ListBuffer[Names#Name]) extends Traverser {
    val importVars = new ListBuffer[Pair[Names#Name,Int]];
    var curAST = 0;
    import compiler.Ident;
    override def traverse(ast: Tree): unit = ast match {
      case Ident(name) => {
        var lastPrevDefsIdx = -1;
        //reporter.info(null,"name="+name,true);
        for(val Pair(lineno,defs) <- prevDefines.toList) yield {
          //reporter.info(null,"line#="+lineno+", defs="+defs,true);
          if (defs.indexOf(name) != -1) lastPrevDefsIdx = lineno
        }
        val foundInPrevDefines = (lastPrevDefsIdx != -1);
        //reporter.info(null,"lastPrevDefsIdx="+lastPrevDefsIdx,true);
        if(foundInPrevDefines) {
           val firstCurDefIdx = definedVars.indexOf(name);
           val foundInDefinedVars = (firstCurDefIdx != -1);
           if((!foundInDefinedVars ||
               (foundInDefinedVars && (firstCurDefIdx > curAST)))
               && (importVars.indexOf(Pair(name,lastPrevDefsIdx)) == -1))
               // to prevent duplicate imports (todo: use find instead of indexOf?)
             importVars += Pair(name,lastPrevDefsIdx);
        }
      }
      case _ => {
          // using case x, instead of case _, we can have: reporter.info(null,"x="+x,true);
          super.traverse(ast)
      }
    }
    override def traverseTrees(asts: List[Tree]): unit =
      asts foreach { curAST = curAST+1; traverse; }
  }
  //todo: unit-test cases
}