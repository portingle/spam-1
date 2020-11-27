package scc

import scala.collection.mutable
import scala.io.Source
import scala.language.postfixOps
import scala.util.parsing.combinator.JavaTokenParsers

object SpamCC {

  def main(args: Array[String]) = {
    if (args.size == 1) {
      compile(args)
    }
    else {
      System.err.println("Assemble ...")
      System.err.println("    usage:  file-name.scc ")
      sys.exit(1)
    }
  }

  private def compile(args: Array[String]) = {
    val fileName = args(0)

    val code = Source.fromFile(fileName).getLines().mkString("\n")

    val scc = new SpamCC()

    val roms = scc.compile(code)
    println("roms : " + roms)
    //
    //    val pw = new PrintWriter(new File(s"${fileName}.rom"))
    //    roms.foreach { line =>
    //      line.foreach { rom =>
    //        pw.write(rom)
    //      }
    //      pw.write("\n")
    //    }
    //    pw.close()

  }
}

class SpamCC extends JavaTokenParsers {
  var varLocn = -1
  val vars = mutable.TreeMap.empty[String, (String, Int)]

  final val NAME_SEPARATOR = "_"

  def compile(code: String): List[String] = {

    parse(program, code) match {
      case Success(matched, _) => {
        //        matched.zipWithIndex.foreach(
        //          l => {
        //            println(">>" + l)
        //          }
        //        )
        matched
      }
      case msg: Failure => {
        sys.error(s"FAILURE: $msg ")

      }
      case msg: Error => {
        sys.error(s"ERROR: $msg")
      }
    }
  }

  def dec: Parser[Int] =
    """-?\d+""".r ^^ { v =>
      v.toInt
    }

  def char: Parser[Int] = "'" ~> ".".r <~ "'" ^^ { v =>
    val i = v.codePointAt(0)
    if (i > 127) throw new RuntimeException(s"asm error: character '$v' codepoint $i is outside the 0-127 range")
    i.toByte
  }

  def hex: Parser[Int] = "$" ~ "[0-9a-hA-H]+".r ^^ { case _ ~ v => Integer.valueOf(v, 16) }

  def bin: Parser[Int] = "%" ~ "[01]+".r ^^ { case _ ~ v => Integer.valueOf(v, 2) }

  def oct: Parser[Int] = "@" ~ "[0-7]+".r ^^ { case _ ~ v => Integer.valueOf(v, 8) }

  def op: Parser[String] = "+" | "-" | "*" | "/" ^^ {
    o => o
  }

  def factor: Parser[Int] = char  | dec | hex | bin | oct | "(" ~> expr <~ ")"

  def expr: Parser[Int] = factor ~  (( op ~ factor)*) ^^ {
    case x ~ list =>
      list.foldLeft(x)( {
        case (acc, "+" ~ i) => acc + i
        case (acc, "*" ~ i) => acc * i
        case (acc, "/" ~ i) => acc / i
        case (acc, "-" ~ i) => acc - i
      })
  }

  def name: Parser[String] = "[a-zA-Z][a-zA-Z0-9_]*".r ^^ (a => a)

  def assignVar(label: String): String = {

    def upd = {
      varLocn += 1
      (label, varLocn)
    }

    vars.getOrElseUpdate(label, upd)._1
  }
  def assignVar(block: Block, name: String): String = {
    val fqn = block.fqn(name)
    val label = assignVar(fqn)
    assignVar(label)
  }

  def loopupVar(label: String): Option[String] = {
    vars.get(label).map(_._1)
  }

  def statementVar: Parser[Block] = "var" ~> name ~ "=" ~ expr ^^ {
    case target ~ _ ~ v =>
      Block("",
        parent => {
          val label = assignVar(parent, target)
          List(s"[:$label] = $v")
        }
      )
  }

  def varExprNE = name ~ op ~ expr ^^ {
    case name ~  op ~ expr => (name ,  op , expr)
  }

  def varExprEN = expr ~ op ~ name  ^^ {
    case expr ~ op ~ name => (name ,  op , expr)
  }

  def varExpr = varExprEN | varExprNE

  def statementVarOp: Parser[Block] = "var" ~> name ~ "=" ~ varExpr ^^ {
    case target ~ _ ~ v =>
      Block("",
        parent => {
          val labelTarget = assignVar(parent, target)
          val srcVarName = v._1
          val op = v._2
          val expr = v._3
          val labelSrcVar = assignVar(parent, srcVarName)
          List(
            s"REGA = [:$labelSrcVar]",
            s"[:$labelTarget] = REGA $op $expr"
          )
        }
      )
  }

  def statementReturn: Parser[Block] = "return" ~> expr ^^ {
    a =>
      Block("",
        _ => {
          List("REGD = " + a)
        }
      )
  }

  def statementReturnName: Parser[Block] = "return" ~> name ^^ {
    n =>
      Block("",
        parent => {
          val label = assignVar(parent, n)
          List(s"REGD = [:$label]")
        }
      )
  }

  def statement: Parser[Block] = statementReturn | statementReturnName | statementVarOp | statementVar

  def statements: Parser[List[Block]] = statement ~ (statement *) ^^ {
    case a ~ b =>
      a +: b
  }

  def function: Parser[Block] = "def " ~> name ~ ("(" ~ ")" ~ ":" ~ "void" ~ "=" ~ "{") ~ statements <~ "}" ^^ {
    case fnName ~ _ ~ c =>
      Block(fnName,
        p => {
          val stmts = c.flatMap {
            b => {
              val newName = p.blockName + NAME_SEPARATOR + fnName
              b.expr(p.copy(blockName = newName))
            }
          }

          val suffix = if (fnName == "main") {
            List("PCHITMP = <:root_end", "PC = >:root_end")
          } else Nil

          stmts ++ suffix
        }
      )
  }

  case class Block(blockName: String, expr: Block => List[String]) {

    def fqn(child: String): String = {
      blockName + NAME_SEPARATOR + child
    }

  }

  def program: Parser[List[String]] = (function +) ^^ {
    fns =>
      val root = Block("root", _ => Nil)
      val asm: List[String] = fns.flatMap(b =>
        b.expr(root)
      )

      val varlist = vars.map(x => s"${x._1}: EQU ${x._2._2}").toList
      varlist ++ asm :+ "root_end:" :+ "END"
  }
}