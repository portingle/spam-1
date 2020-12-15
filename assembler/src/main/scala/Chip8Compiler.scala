/*
* BC_Text can be found here ...
*  https://github.com/stianeklund/chip8/tree/master/roms
*
* IBM Logo ...
* https://github.com/loktar00/chip8/blob/master/roms/IBM%20Logo.ch8
* also in https://gitgud.io/Dorin/pych8/-/tree/master/roms/programs
* */

import asm.EnumParserOps

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.util.matching.Regex
import scala.util.parsing.combinator.JavaTokenParsers

object Chip8Compiler extends EnumParserOps with JavaTokenParsers {

  implicit class Ops(value: String) {
    val charArray: Array[Char] = value.toCharArray

    def b32: Int = Integer.valueOf(value.substring(0, 2), 16)

    def b10: Int = Integer.valueOf(value.substring(2, 4), 16)

    def b210: Int = Integer.valueOf(value.substring(1, 4), 16)

    def b3210: Int = Integer.valueOf(value, 16)

    def hexToInt: Int = Integer.valueOf(value, 16)
  }

  val AddressRegex: Regex = "([0-9A-F][0-9A-F][0-9A-F][0-9A-F]):" r

  val ClearScreenRegex: Regex = "00E0" r
  val ReturnSubRegex: Regex = "00EE" r
  val ObsoleteMachineJumpRegex: Regex = "0([0-9A-F][0-9A-F][0-9A-F])" r
  val JumpRegex: Regex = "1([0-9A-F][0-9A-F][0-9A-F])" r
  val GoSubRegex: Regex = "2([0-9A-F][0-9A-F][0-9A-F])" r
  val SkipIfVxEqNRegex: Regex = "3([0-9A-F])([0-9A-F][0-9A-F])" r
  val SkipIfVxNENRegex: Regex = "4([0-9A-F])([0-9A-F][0-9A-F])" r
  val SkipIfVxEqVyRegex: Regex = "5([0-9A-F])([0-9A-F])0" r
  val SetVxRegex: Regex = "6([0-9A-F])([0-9A-F][0-9A-F])" r
  val AddVxRegex: Regex = "7([0-9A-F])([0-9A-F][0-9A-F])" r
  val SetXEqYRegex: Regex = "8([0-9A-F])([0-9A-F])0" r
  val SkipIfVxNeVyRegex: Regex = "9([0-9A-F])([0-9A-F])0" r
  val SetIndexRegex: Regex = "A([0-9A-F][0-9A-F][0-9A-F])" r
  val DisplayRegex: Regex = "D([0-9A-F])([0-9A-F])([0-9A-F])" r

  def opCode: Parser[Instruction] = "[0-9a-hA-H]{4}".r ^^ {
    case op => op.toUpperCase match {
      case ClearScreenRegex() => ClearScreen(op)
      case JumpRegex(nnn) => Jump(op, nnn.hexToInt)
      case GoSubRegex(nnn) => GoSub(op, nnn.hexToInt)
      case ReturnSubRegex() => ReturnSub(op)
      case SkipIfVxEqNRegex(x, nn) => SkipIfXEqN(op, x.hexToInt, nn.hexToInt)
      case SkipIfVxNENRegex(xReg, nn) => SkipIfXNeN(op, xReg.hexToInt, nn.hexToInt)
      case SkipIfVxEqVyRegex(xReg, yReg) => SkipIfXEqY(op, xReg.hexToInt, yReg.hexToInt)
      case SkipIfVxNeVyRegex(xReg, yReg) => SkipIfXNeY(op, xReg.hexToInt, yReg.hexToInt)
      case SetVxRegex(xReg, nn) => SetX(op, xReg.hexToInt, nn.hexToInt)
      case AddVxRegex(xReg, nn) => AddX(op, xReg.hexToInt, nn.hexToInt)
      case SetIndexRegex(nnn) => SetIndex(op, nnn.hexToInt)
      case DisplayRegex(xReg, yReg, n) => Display(op, xReg.hexToInt, yReg.hexToInt, n.hexToInt)
      case ObsoleteMachineJumpRegex(nnn) => ObsoleteMachineJump(op, nnn.hexToInt)
      case SetXEqYRegex(xReg, yReg) => SetXEqY(op, xReg.hexToInt, yReg.hexToInt)
      case _ => NotRecognised(op)
    }
  }

  def address: Parser[Address] = "[0-9a-hA-H]{4}:".r ^^ {
    case AddressRegex(code) => Address(Integer.valueOf(code, 16))
  }


  case class Line(addr: Address, instruction: Instruction) {
    override def toString: String = {
      s"Line( $addr  $instruction )"
    }
  }

  private var nextAddress: Address = Address(0x200)

  def line: Parser[Line] = opt(address) ~ opCode ^^ {
    case a ~ o => {
      val thisAddress = a.getOrElse(nextAddress) // two bytes per instruction
      nextAddress = thisAddress.copy(thisAddress.location + 2)
      Line(thisAddress, o)
    }
  }

  case class Address(location: Int) {
    override def toString: String = {
      val hex = location.toHexString
      val pad = "0" * (4 - hex.length)
      s"$pad$hex"
    }
  }

  sealed trait Instruction {
    def op: String
  }

  case class GoSub(op: String, nnn: Int) extends Instruction

  case class Jump(op: String, nnn: Int) extends Instruction

  case class ObsoleteMachineJump(op: String, value: Int) extends Instruction

  case class ReturnSub(op: String) extends Instruction

  case class ClearScreen(op: String) extends Instruction

  case class SkipIfXEqN(op: String, xReg: Int, nn: Int) extends Instruction

  case class SkipIfXNeN(op: String, xReg: Int, nn: Int) extends Instruction

  case class SkipIfXEqY(op: String, xReg: Int, vReg: Int) extends Instruction

  case class SkipIfXNeY(op: String, xReg: Int, vReg: Int) extends Instruction

  case class SetX(op: String, xReg: Int, nn: Int) extends Instruction

  case class AddX(op: String, xReg: Int, nn: Int) extends Instruction // Does not set carry

  case class SetIndex(op: String, nnn: Int) extends Instruction

  case class Display(op: String, xReg: Int, vReg: Int, n: Int) extends Instruction

  case class NotRecognised(op: String) extends Instruction

  def program: Parser[List[Line]] = rep(line) ^^ {
    fns => fns
  }



  def compile(code: List[Short]): List[Line] = {

    val prog = code.map { x =>   f"$x%04x" }.mkString("\n")

    parse(program, prog) match {
      case Success(matched, _) =>
        matched
      case msg: Failure =>
        sys.error(s"FAILURE: $msg ")
      case msg: Error =>
        sys.error(s"ERROR: $msg")
    }
  }


  case class SetXEqY(op: String, xReg: Int, yReg: Any) extends Instruction

}

object C8 extends App {
  val asm: List[Short] = Loader.read(Loader.IBMLogo)

  val ast: List[Chip8Compiler.Line] = Chip8Compiler.compile(asm)
  ast.zipWithIndex.foreach(println)

  Chip8Emulator.run(ast)

}

object Chip8Emulator {

  import Chip8Compiler._

  val BLANK = ' '
  val PIXEL = '#'
  var index = 0x200

  private val register = ListBuffer.empty[Int]
  private val memory = ListBuffer.empty[Int] // char is unsigned

  def run(program: List[Line]): Unit = {

    // init memory to 0
    val rom = ListBuffer.empty[Instruction]

    val maxMem = 1 + 0x200 + (program.length * 2)
    (0 to maxMem).foreach{ _ =>
      rom.append(null)
    }

    (0 to maxMem).foreach { _ =>
      memory.append(0)
    }

    // load program

    program.foreach {
      case Chip8Compiler.Line(i, o) =>

        rom(i.location) = o

        val nh = o.op.b32.toChar
        memory(i.location) = nh

        val nl = o.op.b10.toChar
        memory(i.location + 1) = nl
    }


    val zeros: List[Int] = (0 to 15).map { _ => 0 }.toList
    register.appendAll(zeros)

    var pc = 0x200


    var running = true
    while (running) {
      var skipPcInc = false
      val inst = rom(pc)

      inst match {
        case ClearScreen(_) =>
          clearScreen()
        case Jump(_, nnn) =>
          pc = nnn
          skipPcInc = true
        case GoSub(_, nnn) =>
          push(pc)
          pc = nnn
          skipPcInc = true
        case ReturnSub(_) =>
          pc = pop()
        case SkipIfXEqN(_, vx, nn) =>
          val xVal: Int = register(vx)
          if (xVal == nn) {
            pc += 2
          }
        case SkipIfXNeN(_, vx, nn) =>
          val xVal: Int = register(vx)
          if (xVal != nn) {
            pc += 2
          }
        case SkipIfXEqY(_, vx, vy) =>
          val xVal: Int = register(vx)
          val yVal: Int = register(vy)
          if (xVal == yVal) {
            pc += 2
          }
        case SkipIfXNeY(_, vx, vy) =>
          val xVal: Int = register(vx)
          val yVal: Int = register(vy)
          if (xVal != yVal) {
            pc += 2
          }
        case SetX(_, vx, nn) =>
          register(vx) = nn
        case AddX(_, vx, nn) =>
          register(vx) = register(vx) + nn
        case SetIndex(_, nnn) =>
          index = nnn
        case Display(_, vx, vy, n) =>
          val xVal: Int = register(vx)
          val yVal: Int = register(vy)

          drawSprite(n, xVal, yVal)
          refreshScreen()
        case ignored =>
          //println("ignoring " + ignored)
      }

      if (!skipPcInc)
        pc += 2 // each instruction is two bytes
    }
  }

  def setPixel(x: Int, y: Int): Boolean = {
    val isSet = (screen(y)(x) != BLANK)

    // xor
    if (isSet)
      screen(y)(x) = BLANK
    else
      screen(y)(x) = PIXEL

    isSet
  }


  private def drawSprite(height: Int, xVal: Int, yVal: Int): Unit = {

    register(0xF) = 0 // no collision yet

    (0 until height).foreach { y =>
      var spr: Int = memory(index + y)

      (0 to 7).foreach {
        x =>
          // look at top bit
          val bit = spr & 0x80
          val isSet = bit > 0
          if (isSet) {
            val collision = setPixel(xVal + x, yVal + y)
            if (collision) {
              register(0xF) = 1
            }
          }
          spr <<= 1
      }
    }
  }

  val HEIGHT = 32
  val WIDTH = 60
  var screen: mutable.Seq[mutable.Buffer[Char]] = emptyScreenBuffer

  def emptyScreenBuffer: mutable.Seq[mutable.Buffer[Char]] = {
    (0 until HEIGHT).map {
      d =>
        val blank = BLANK.toString
        (blank * WIDTH).toBuffer
    }.toBuffer
  }


  def clearScreen(): Unit = {
    screen = emptyScreenBuffer
  }

  val stack: mutable.Stack[Int] = mutable.Stack.empty[Int]

  def push(i: Int): Unit = stack.push(i)

  def pop(): Int = stack.pop()

  def refreshScreen(): Unit = {
    println("--------\n")
    (0 until HEIGHT).foreach { y =>
      val str = screen(y).mkString(" ")
      println(str)
    }
  }
}