package scc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.MethodOrderer.MethodName
import org.junit.jupiter.api.{Test, TestMethodOrder}
import verification.Checks._
import verification.Verification._

@TestMethodOrder(classOf[MethodName])
class SpamCCTest {

  def split(s: String): List[String] = {
    val strings = s.split("\n")
    strings
      .map(_.stripTrailing().stripLeading())
      .filterNot(_.isBlank).toList
  }

  def assertSame(expected: List[String], actual: List[String]): Unit = {
    println("\nCOMPARING ASM:")
    if (expected != actual) {
      println("Expected: " + expected)
      println("Actual  : " + actual)

      val e = expected.map(_.stripTrailing().stripLeading()).mkString("\n")
      val a = actual.map(_.stripTrailing().stripLeading()).mkString("\n")
      assertEquals(e, a)
    }
  }

  @Test
  def varEq1(): Unit = {

    val lines =
      """fun main() {
        | var a=1;
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines)

    val expected = split(
      """
        |root_function_main___VAR_RETURN_HI: EQU   0
        |root_function_main___VAR_RETURN_HI: BYTES [0]
        |root_function_main___VAR_RETURN_LO: EQU   1
        |root_function_main___VAR_RETURN_LO: BYTES [0]
        |root_function_main___VAR_a: EQU   2
        |root_function_main___VAR_a: BYTES [0]
        |PCHITMP = < :ROOT________main_start
        |PC = > :ROOT________main_start
        |ROOT________main_start:
        |root_function_main___LABEL_START:
        |[:root_function_main___VAR_a] = 1
        |PCHITMP = <:root_end
        |PC = >:root_end
        |root_end:
        |END""".stripMargin)

    assertSame(expected, actual)
  }
  @Test
  def varEqVar(): Unit = {

    val lines =
      """fun main() {
        | var a=1;
        | var b=a;
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines)

    val expected = split(
      """
        |root_function_main___VAR_RETURN_HI: EQU   0
        |root_function_main___VAR_RETURN_HI: BYTES [0]
        |root_function_main___VAR_RETURN_LO: EQU   1
        |root_function_main___VAR_RETURN_LO: BYTES [0]
        |root_function_main___VAR_a: EQU   2
        |root_function_main___VAR_a: BYTES [0]
        |root_function_main___VAR_b: EQU   3
        |root_function_main___VAR_b: BYTES [0]
        |PCHITMP = < :ROOT________main_start
        |PC = > :ROOT________main_start
        |ROOT________main_start:
        |root_function_main___LABEL_START:
        |[:root_function_main___VAR_a] = 1
        |REGA = [:root_function_main___VAR_a]
        |[:root_function_main___VAR_b] = REGA
        |PCHITMP = <:root_end
        |PC = >:root_end
        |root_end:
        |END""".stripMargin)

    assertSame(expected, actual)
  }

  @Test
  def varEqConstExpr(): Unit = {

    val lines =
      """
        |fun main() {
        | var a=1;
        | var b=64+1;
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines)

    val expected = split(
      """
        |root_function_main___VAR_RETURN_HI: EQU   0
        |root_function_main___VAR_RETURN_HI: BYTES [0]
        |root_function_main___VAR_RETURN_LO: EQU   1
        |root_function_main___VAR_RETURN_LO: BYTES [0]
        |root_function_main___VAR_a: EQU   2
        |root_function_main___VAR_a: BYTES [0]
        |root_function_main___VAR_b: EQU   3
        |root_function_main___VAR_b: BYTES [0]
        |PCHITMP = < :ROOT________main_start
        |PC = > :ROOT________main_start
        |ROOT________main_start:
        |root_function_main___LABEL_START:
        |[:root_function_main___VAR_a] = 1
        |[:root_function_main___VAR_b] = 65
        |PCHITMP = <:root_end
        |PC = >:root_end
        |root_end:
        |END""".stripMargin)

    assertSame(expected, actual)
  }


  @Test
  def twoFunctions(): Unit = {

    val lines =
      """
        |fun main() {
        | var a=1;
        | var b=2;
        |}
        |fun other() {
        | var a=1;
        | var b=2;
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines)

    val expected = split(
      """root_function_main___VAR_RETURN_HI: EQU   0
        |root_function_main___VAR_RETURN_HI: BYTES [0]
        |root_function_main___VAR_RETURN_LO: EQU   1
        |root_function_main___VAR_RETURN_LO: BYTES [0]
        |root_function_main___VAR_a: EQU   2
        |root_function_main___VAR_a: BYTES [0]
        |root_function_main___VAR_b: EQU   3
        |root_function_main___VAR_b: BYTES [0]
        |root_function_other___VAR_RETURN_HI: EQU   4
        |root_function_other___VAR_RETURN_HI: BYTES [0]
        |root_function_other___VAR_RETURN_LO: EQU   5
        |root_function_other___VAR_RETURN_LO: BYTES [0]
        |root_function_other___VAR_a: EQU   6
        |root_function_other___VAR_a: BYTES [0]
        |root_function_other___VAR_b: EQU   7
        |root_function_other___VAR_b: BYTES [0]
        |PCHITMP = < :ROOT________main_start
        |PC = > :ROOT________main_start
        |ROOT________main_start:
        |root_function_main___LABEL_START:
        |[:root_function_main___VAR_a] = 1
        |[:root_function_main___VAR_b] = 2
        |PCHITMP = <:root_end
        |PC = >:root_end
        |root_function_other___LABEL_START:
        |[:root_function_other___VAR_a] = 1
        |[:root_function_other___VAR_b] = 2
        |PCHITMP = [:root_function_other___VAR_RETURN_HI]
        |PC = [:root_function_other___VAR_RETURN_LO]
        |root_end:
        |END""".stripMargin)

    // not using Ex function as it assumes main is last function
    assertSame(expected, actual)
  }

  @Test
  def varEqSimpleTwoArgExpr(): Unit = {

    val lines =
      """
        |fun main() {
        |  // a = 63 + 2 = 'A'
        |  var a = 63 + 2;
        |
        |  // b = a + 1 = 'B'
        |  var b = a + 1;
        |
        |  // c = 1 + b = 'C'
        |  var c = 1 + b;
        |
        |  // d = c the d++
        |  var d = c;
        |  d = d + 1;
        |
        |  // e = a + (b/2) = 'b'
        |  var e = a + (b/2);
        |
        |  // should print 'A'
        |  putchar(a)
        |  // should print 'B'
        |  putchar(b)
        |  // should print 'C'
        |  putchar(c)
        |  // should print 'D'
        |  putchar(d)
        |  // should print 'b'
        |  putchar(e)
        |
        |  // should shift left twice to become the '@' char
        |  a = %00010000;
        |  b = 2;
        |  var at = a A_LSL_B b;
        |  // should print '@'
        |  putchar(at)
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, verbose = true, outputCheck = str => {
      checkTransmittedChar(str, 'A')
      checkTransmittedChar(str, 'B')
      checkTransmittedChar(str, 'C')
      checkTransmittedChar(str, 'D')
      checkTransmittedChar(str, 'b')
      checkTransmittedChar(str, '@')
    })

    val expected = split("")

    //assertSame(expected, actual)
  }

  @Test
  def varEqNestedExpr(): Unit = {

    val lines =
      """fun main() {
        |  var a = 64;
        |  var b = 1 + (a + 3);
        |  putchar(b)
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, outputCheck = str => {
      checkTransmittedChar(str, 'D')
    })

    val expected = split("")

    // assertSame(expected, actual)
  }

  @Test
  def putchar(): Unit = {

    val lines =
      """
        |fun main() {
        |  putchar(65)
        |  putchar('B')
        |  var c=67;
        |  putchar(c)
        |  putchar(c+1)
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, verbose = true, outputCheck = str => {
      checkTransmittedChar(str, 'A')
      checkTransmittedChar(str, 'B')
      checkTransmittedChar(str, 'C')
      checkTransmittedChar(str, 'D')
    })

    val expected = split("")

    // assertSame(expected, actual)
  }

  @Test
  def getchar(): Unit = {

    val lines =
      """
        |fun main() {
        |  var g = getchar();
        |  putchar(g)
        |}
        |""".stripMargin

    compile(lines, verbose = true, timeout = 1000, dataIn = List("t1", "rA"), outputCheck = {
      str =>
        checkTransmittedChar(str, 'A')
    })
  }

  @Test
  def valEqLogical(): Unit = {

    val lines =
      """
        |fun main() {
        | var a=1>0;
        | putchar(a)
        |
        | a=0>1;
        | putchar(a)
        |
        | a=0==1;
        | putchar(a)
        |
        | a=1==1;
        | putchar(a)
        |
        | a=%1010 & %1100;
        | putchar(a)

        | a=%1010 | %1100;
        | putchar(a)
        |}
        |""".stripMargin

    compile(lines, verbose = true, outputCheck = {
      str =>
        checkTransmittedL('h', str, List("01", "00", "00", "01", "08", "0e"))
    })
  }

  @Test
  def whileLoopCond(): Unit = {

    val lines =
      """
        |fun main() {
        | var a=10;
        | while(a>0) {
        |   a=a-1;
        |   putchar(a)
        | }
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, verbose = true, outputCheck = {
      lines =>
        checkTransmittedDecs(lines, List(9, 8, 7, 6, 5, 4, 3, 2, 1, 0))
    })

    val expected = split("")

    //assertSame(expected, actual)
  }


  @Test
  def whileLoopTrueIfBreak(): Unit = {

    val lines =
      """
        |fun main() {
        | var a = 1;
        | while(true) {
        |   a = a + 1;
        |
        |   if (a>10) {
        |     break
        |   }
        |   putchar(a)
        |
        | }
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, outputCheck = {
      lines =>
        checkTransmittedDecs(lines, List(2, 3, 4, 5, 6, 7, 8, 9, 10))
    })

    val expected = split("")

    //assertSame(expected, actual)
  }

  @Test
  def functionCalls(): Unit = {

    val lines =
      """
        |// START FN COMMAND
        |
        |fun print(a1 out, a2, a3, a4) {
        | // FN COMMENT
        | var d = a1;
        | //d = a2;
        | putchar(d)
        | putchar(a2)
        | putchar(a3)
        | putchar(a4)
        |
        | // ascii 33 dec
        | a1 = '!';
        | // END FN COMMENT
        |}
        |
        |fun main() {
        | var arg1 = 'A';
        | var arg2 = 1;
        |
        | // CALLING PRINT - 63 is '?'
        | print(arg1, arg2+arg1, 63, arg1+4)
        |
        | // CALLING PUT CHAR OF OUT VALUE
        | putchar(arg1)
        |
        |}
        |
        |// END  COMMAND
        |""".stripMargin

    val actual: List[String] = compile(lines, verbose = true, quiet = true, outputCheck = str => {
      checkTransmittedChar(str, 'A')
      checkTransmittedChar(str, 'B')
      checkTransmittedChar(str, '?')
      checkTransmittedChar(str, 'E')
      checkTransmittedChar(str, '!')
    })

    val expected = split("")

    //assertSame(expected, actual)
  }

  @Test
  def functionCalls2Deep(): Unit = {

    val lines =
      """
        |fun depth2(b1 out) {
        | b1 = b1 + 1;
        |}
        |
        |fun depth1(a1 out) {
        | depth2(a1)
        |}
        |
        |fun main() {
        | var arg1 = 'A';
        | depth1(arg1)
        | putchar(arg1)
        |}
        |
        |// END  COMMAND
        |""".stripMargin

    val actual: List[String] = compile(lines, quiet = true, outputCheck = {
      lines =>
        checkTransmittedChars(lines, List("B"))
    })

    val expected = split("")

    //assertSame(expected, actual)
  }

  @Test
  def references(): Unit = {

    val lines =
      """
        |fun main() {
        |
        | // define string
        | var even = "Even\0";
        | var odd = "Odd\0";
        |
        | // value at 16 bit var ptr becomes address of array odd
        | ref ptr = odd;
        |
        | var i = 10;
        | while (i>0) {
        |   i = i - 1;
        |   var c = i % 2;
        |   if (c == 0) {
        |       // set pointer to point at even
        |       ptr = even;
        |   }
        |   if (c != 0) {
        |      ptr = odd;
        |   }
        |   puts(ptr)
        | }
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, verbose = true, quiet = true, outputCheck = str => {
      val value: List[String] = "OddEvenOddEvenOddEvenOddEvenOddEven".toList.map(_.toString)
      checkTransmittedL('c', str, value)
    })

    val expected = split("")

    //assertSame(expected, actual)
  }

  @Test
  def referenceLong(): Unit = {

    val data = (0 to 255) map {x =>
      f"$x%02x"
    } mkString("")

    val lines =
      s"""
        |fun main() {
        |
        | // define string
        | var string = "$data\\0";
        |
        | // value at 16 bit var ptr becomes address of array odd
        | uint16 addr16 = string;
        | uint16 idx = 0;
        | uint8 c = string[idx];
        | ref ptr = string;
        |
        | var i = 255;
        | while (i>0) {
        |   var lo = ptr;
        |   ptr  = ptr + 1;
        |   var hi = ptr;
        |   ptr  = ptr + 1;
        |
        |   i = i - 1;
        | }
        |}
        |""".stripMargin

    println(lines)
    val actual: List[String] = compile(lines, verbose = true, quiet = true, outputCheck = str => {
      val value: List[String] = "OddEvenOddEvenOddEvenOddEvenOddEven".toList.map(_.toString)
      checkTransmittedL('c', str, value)
    })

    val expected = split("")

    //assertSame(expected, actual)
  }

  @Test
  def stringIndexing(): Unit = {

    val lines =
      """
        |fun main() {
        | // define string
        | var string = "ABCD\0";
        |
        | // index by literal
        | var ac = string[0];
        |
        | // index by variable
        | var b = 1;
        | var bc = string[b];
        |
        | // print values so we can test correct values selected
        | var d = 3;
        | putchar(ac)
        | putchar(bc)
        | putchar(string[2])
        | putchar(string[d])
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, verbose = false, quiet = true, outputCheck = str => {
      checkTransmittedChar(str, 'A')
      checkTransmittedChar(str, 'B')
      checkTransmittedChar(str, 'C')
      checkTransmittedChar(str, 'D')
    })

    val expected = split("")

    //assertSame(expected, actual)
  }

  @Test
  def stringIteration(): Unit = {

    val lines =
      """
        |fun main() {
        | // define string
        | var string = "ABCD\0";
        |
        | var idx = 0;
        | var c = string[idx];
        | while (c != 0) {
        |   putchar(c)
        |   idx = idx + 1;
        |   c = string[idx];
        | }
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, verbose = true, quiet = true, outputCheck = str => {
      checkTransmittedChar(str, 'A')
      checkTransmittedChar(str, 'B')
      checkTransmittedChar(str, 'C')
      checkTransmittedChar(str, 'D')
    })

    val expected = split("")

    // assertSame(expected, actual)
  }

  @Test
  def snake(): Unit = {
    import terminal.UARTTerminal._

    val lines =
      s"""fun main() {
         | uint8 loop = 0;
         | while ( loop <= 2) {
         |  uint8 a = 33 + loop;
         |
         |  uint8 b = 2;
         |  while ( b > 0 ) {
         |   putchar(${DO_RIGHT.toInt})
         |   putchar( a )
         |   b = b - 1;
         |  }
         |  b = 2;
         |  while ( b > 0 ) {
         |   putchar(${DO_DOWN.toInt})
         |   putchar( a )
         |   b = b - 1;
         |  }
         |  b = 2;
         |  while ( b > 0 ) {
         |   putchar(${DO_LEFT.toInt})
         |   putchar( a )
         |   b = b - 1;
         |  }
         |
         |  b = 2;
         |  while ( b > 0 ) {
         |   putchar(${DO_UP.toInt})
         |   putchar( a )
         |   b = b - 1;
         |  }
         |  putchar(${DO_RIGHT.toInt})
         |  putchar(${DO_DOWN.toInt})
         |
         |  loop = loop + 1;
         | }
         |}
         |
         |// END  COMMAND
         |""".stripMargin

    compile(lines, verbose = true, quiet = true, outputCheck = str => {
      checkTransmittedDec(str, DO_RIGHT)
      checkTransmittedDec(str, DO_DOWN)
      checkTransmittedDec(str, DO_LEFT)
      checkTransmittedDec(str, DO_UP)
    })

  }

  @Test
  def puts(): Unit = {

    val lines =
      """
        |fun main() {
        | // define string
        | var string = "ABCD\0";
        | puts(string)
        |}
        |""".stripMargin

    val actual: List[String] = compile(lines, verbose = true, quiet = true, outputCheck = str => {
      checkTransmittedChar(str, 'A')
      checkTransmittedChar(str, 'B')
      checkTransmittedChar(str, 'C')
      checkTransmittedChar(str, 'D')
    })

    val expected = split("")
    //  assertSame(expected, actual)
  }
}
