package scala.io

import scala.collection.mutable.{HashMap,ArrayBuffer}

/**
  Pickler combinators.
  Based on a Haskell library by Andrew Kennedy, see http://research.microsoft.com/~akenn/fun/index.html.

  Author: Philipp Haller <philipp.haller@epfl.ch>
  */

object BytePickle {
  abstract class SPU[t] {
    def appP(a: t, state: PicklerState): PicklerState;
    def appU(state: UnPicklerState): Pair[t, UnPicklerState];
  }

  def pickle[t](p: SPU[t], a: t): Array[byte] =
    p.appP(a, new PicklerState(new Array[byte](0), new PicklerEnv)).stream;

  def unpickle[t](p: SPU[t], stream: Array[byte]): t =
    p.appU(new UnPicklerState(stream, new UnPicklerEnv))._1;

  abstract class PU[t] {
    def appP(a: t, state: Array[byte]): Array[byte];
    def appU(state: Array[byte]): Pair[t, Array[byte]];
  }

  def upickle[t](p: PU[t], a: t): Array[byte] =
    p.appP(a, new Array[byte](0));

  def uunpickle[t](p: PU[t], stream: Array[byte]): t =
    p.appU(stream)._1;

  class PicklerEnv extends HashMap[Any, int] {
    private var cnt: int = 64;
    def nextLoc() = { cnt = cnt + 1; cnt };
  }

  class UnPicklerEnv extends HashMap[int, Any] {
    private var cnt: int = 64;
    def nextLoc() = { cnt = cnt + 1; cnt };
  }

  class PicklerState(val stream: Array[byte], val dict: PicklerEnv) {}
  class UnPicklerState(val stream: Array[byte], val dict: UnPicklerEnv) {}

  abstract class RefDef;
  case class Ref() extends RefDef;
  case class Def() extends RefDef;

  def refDef: PU[RefDef] = new PU[RefDef] {
    def appP(b: RefDef, s: Array[byte]): Array[byte] =
      b match {
        case Ref() => Array.concat(s, (List[byte](0)).toArray)
        case Def() => Array.concat(s, (List[byte](1)).toArray)
      };
    def appU(s: Array[byte]): Pair[RefDef, Array[byte]] =
      if (s(0) == 0) Pair(Ref(), s.subArray(1, s.length))
      else Pair(Def(), s.subArray(1, s.length));
  }

  val REF = 0
  val DEF = 1

  def unat: PU[int] = new PU[int] {
    def appP(n: int, s: Array[byte]): Array[byte] =
      Array.concat(s, nat2Bytes(n));
    def appU(s: Array[byte]): Pair[int, Array[byte]] = {
      var num = 0
      def readNat: int = {
        var b = 0;
        var x = 0;
        do {
          b = s(num)
          num = num + 1
          x = (x << 7) + (b & 0x7f);
        } while ((b & 0x80) != 0);
        x
      }
      Pair(readNat, s.subArray(num, s.length))
    }
  }

  def share[a](pa: SPU[a]): SPU[a] = new SPU[a] {
    def appP(v: a, state: PicklerState): PicklerState = {
      /*
      - is there some value equal to v associated with a location l in the pickle environment?
      - yes: write REF-tag to outstream together with l
      - no:
          write DEF-tag to outstream
          record current location l of outstream
          --> serialize value
          add entry to pickle environment, mapping v onto l
      */
      val pe = state.dict
      pe.get(v) match {
        case None =>
          //Console.println("" + v + " is new")
	  //Console.println("writing DEF...")
          val sPrime = refDef.appP(Def(), state.stream)
          val l = pe.nextLoc()

          //Console.println("applying pickler to state " + sPrime)
          val sPrimePrime = pa.appP(v, new PicklerState(sPrime, pe))

          //Console.println("updating dict (" + l + ") for " + v)
          pe.update(v, l)

          return sPrimePrime
        case Some(l) =>
          //Console.println("writing REF...")
          val sPrime = refDef.appP(Ref(), state.stream)

          //Console.println("writing location to state " + sPrime)
          return new PicklerState(unat.appP(l, sPrime), pe)
      }
    }
    def appU(state: UnPicklerState): Pair[a, UnPicklerState] = {
      /*
      - first, read tag (i.e. DEF or REF)
      - if REF:
          read location l
          look up resulting value in unpickler environment
      - if DEF:
          record location l of input stream
          --> deserialize value v with argument deserializer
          add entry to unpickler environment, mapping l onto v
      */
      val upe = state.dict
      val res = refDef.appU(state.stream)
      res._1 match {
        case Def() =>
          val l = upe.nextLoc
          val res2 = pa.appU(new UnPicklerState(res._2, upe))
          upe.update(l, res2._1)
          return res2
        case Ref() =>
          val res2 = unat.appU(res._2)  // read location
          upe.get(res2._1) match {     // lookup value in unpickler env
            case None => error("invalid unpickler environment"); return null
            case Some(v) => return Pair(v.asInstanceOf[a], new UnPicklerState(res2._2, upe))
          }
      }
    }
  }

  def ulift[t](x: t): PU[t] = new PU[t] {
    def appP(a: t, state: Array[byte]): Array[byte] =
      if (x != a) { error("value to be pickled (" + a + ") != " + x); state }
      else state;
    def appU(state: Array[byte]) = Pair(x, state);
  }

  def lift[t](x: t): SPU[t] = new SPU[t] {
    def appP(a: t, state: PicklerState): PicklerState =
      if (x != a) { /*error("value to be pickled (" + a + ") != " + x);*/ state }
      else state;
    def appU(state: UnPicklerState) = Pair(x, state);
  }

  def usequ[t,u](f: u => t, pa: PU[t], k: t => PU[u]): PU[u] = new PU[u] {
    def appP(b: u, s: Array[byte]): Array[byte] = {
      val a = f(b)
      val sPrime = pa.appP(a, s)
      val pb = k(a)
      val sPrimePrime = pb.appP(b, sPrime)
      sPrimePrime
    }
    def appU(s: Array[byte]): Pair[u, Array[byte]] = {
      val resPa = pa.appU(s)
      val a = resPa._1
      val sPrime = resPa._2
      val pb = k(a)
      pb.appU(sPrime)
    }
  }

  def sequ[t,u](f: u => t, pa: SPU[t], k: t => SPU[u]): SPU[u] = new SPU[u] {
    def appP(b: u, s: PicklerState): PicklerState = {
      val a = f(b)
      //Console.println("pickling " + a + ", s: " + s.stream)
      val sPrime = pa.appP(a, s)
      val pb = k(a)
      //Console.println("pickling " + b + ", s: " + s.stream)
      pb.appP(b, sPrime)
    }
    def appU(s: UnPicklerState): Pair[u, UnPicklerState] = {
      val resPa = pa.appU(s)
      val a = resPa._1
      val sPrime = resPa._2
      val pb = k(a)
      pb.appU(sPrime)
    }
  }

  def upair[a,b](pa: PU[a], pb: PU[b]): PU[Pair[a,b]] = {
    def fst(p: Pair[a,b]): a = p._1;
    def snd(p: Pair[a,b]): b = p._2;
    usequ(fst, pa, (x: a) => usequ(snd, pb, (y: b) => ulift(Pair(x, y))))
  }

  def pair[a,b](pa: SPU[a], pb: SPU[b]): SPU[Pair[a,b]] = {
    def fst(p: Pair[a,b]): a = p._1;
    def snd(p: Pair[a,b]): b = p._2;
    sequ(fst, pa, (x: a) => sequ(snd, pb, (y: b) => lift(Pair(x, y))))
  }

  def triple[a,b,c](pa: SPU[a], pb: SPU[b], pc: SPU[c]): SPU[Triple[a,b,c]] = {
    def fst(p: Triple[a,b,c]): a = p._1;
    def snd(p: Triple[a,b,c]): b = p._2;
    def trd(p: Triple[a,b,c]): c = p._3;

    sequ(fst, pa,
         (x: a) => sequ(snd, pb,
         (y: b) => sequ(trd, pc,
         (z: c) => lift(Triple(x, y, z)))))
  }

  def uwrap[a,b](i: a => b, j: b => a, pa: PU[a]): PU[b] =
    usequ(j, pa, (x: a) => ulift(i(x)));

  def wrap[a,b](i: a => b, j: b => a, pa: SPU[a]): SPU[b] =
    sequ(j, pa, (x: a) => lift(i(x)));

  def appendByte(a: Array[byte], b: int): Array[byte] = {
    Array.concat(a, (List[byte](b.asInstanceOf[byte])).toArray)
  }

  def nat2Bytes(x: int): Array[byte] = {
    val buf = new ArrayBuffer[byte]
    def writeNatPrefix(x: int): unit = {
      val y = x >>> 7;
      if (y != 0) writeNatPrefix(y);
      buf += ((x & 0x7f) | 0x80).asInstanceOf[byte];
    }
    val y = x >>> 7;
    if (y != 0) writeNatPrefix(y);
    buf += (x & 0x7f).asInstanceOf[byte];
    buf.toArray
  }

  def nat: SPU[int] = new SPU[int] {
    def appP(n: int, s: PicklerState): PicklerState = {
      new PicklerState(Array.concat(s.stream, nat2Bytes(n)), s.dict);
    }
    def appU(s: UnPicklerState): Pair[int,UnPicklerState] = {
      var num = 0
      def readNat: int = {
        var b = 0;
        var x = 0;
        do {
          b = s.stream(num)
          num = num + 1
          x = (x << 7) + (b & 0x7f);
        } while ((b & 0x80) != 0);
        x
      }
      Pair(readNat, new UnPicklerState(s.stream.subArray(num, s.stream.length), s.dict))
    }
  }

  def byte: SPU[byte] = new SPU[byte] {
    def appP(b: byte, s: PicklerState): PicklerState =
      new PicklerState(Array.concat(s.stream, (List[byte](b)).toArray), s.dict);
    def appU(s: UnPicklerState): Pair[byte, UnPicklerState] =
      Pair(s.stream(0), new UnPicklerState(s.stream.subArray(1, s.stream.length), s.dict));
  }

  def string: SPU[String] =
    share(wrap((a:Array[byte]) => UTF8Codec.decode(a, 0, a.length), (s:String) => UTF8Codec.encode(s), bytearray));

  def bytearray: SPU[Array[byte]] = {
    wrap((l:List[byte]) => l.toArray, .toList, list(byte))
  }

  def bool: SPU[boolean] = {
    def toEnum(b: boolean) = if (b) 1 else 0;
    def fromEnum(n: int) = if (n == 0) false else true;
    wrap(fromEnum, toEnum, nat)
  }

  def ufixedList[a](pa: PU[a])(n: int): PU[List[a]] = {
    def pairToList(p: Pair[a,List[a]]): List[a] =
      p._1 :: p._2;
    def listToPair(l: List[a]): Pair[a,List[a]] =
      l match { case x :: xs => Pair(x, xs) }

    if (n == 0) ulift(Nil)
    else
      uwrap(pairToList, listToPair, upair(pa, ufixedList(pa)(n-1)))
  }

  def fixedList[a](pa: SPU[a])(n: int): SPU[List[a]] = {
    def pairToList(p: Pair[a,List[a]]): List[a] =
      p._1 :: p._2;
    def listToPair(l: List[a]): Pair[a,List[a]] =
      l match { case x :: xs => Pair(x, xs) }

    if (n == 0) lift(Nil)
    else
      wrap(pairToList, listToPair, pair(pa, fixedList(pa)(n-1)))
  }

  def list[a](pa: SPU[a]): SPU[List[a]] =
    sequ((l: List[a])=>l.length, nat, fixedList(pa));

  def ulist[a](pa: PU[a]): PU[List[a]] =
    usequ((l:List[a]) => l.length, unat, ufixedList(pa));

  def data[a](tag: a => int, ps: List[()=>SPU[a]]): SPU[a] =
    sequ(tag, nat, (x: int)=> ps.apply(x)());
}