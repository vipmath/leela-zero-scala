package leelazero.board

import leelazero.TestUtil.clean
import FastBoard._
import org.scalatest.FunSuite

class FastBoardSerializerSuite extends FunSuite {

  test("Serialize an empty 3x3 board") {
    val b = new FastBoard(3)
    val fbs = new FastBoardSerializer(b)
    assertResult(clean("""
       |   a b c
       | 3 . . .  3
       | 2 . . .  2
       | 1 . . .  1
       |   a b c
       |
       |""")) { fbs.serialize() }
  }

  test("Serialize 3x3 board with one move played") {
    val b = new FastBoard(3)
    val fbs = new FastBoardSerializer(b)
    assertResult(clean("""
       |   a b c
       | 3 . . .  3
       | 2(.). .  2
       | 1 . . .  1
       |   a b c
       |
       |""")) { fbs.serialize(lastMove = 11) }
  }

  test("Serialize filled 3x3 board with 2, 2 played last") {
    val b = createFilled3x3Board()
    val fbs = new FastBoardSerializer(b)
    assertResult(clean("""
       |   a b c
       | 3 . .(X) 3
       | 2 O X X  2
       | 1 . O .  1
       |   a b c
       |
       |""")) { fbs.serialize(lastMove = b.getVertex(2, 2)) }
  }

  test("toLibertiesString filled 3x3 board with 2, 2 played last") {
    val b = createFilled3x3Board()
    val fbs = new FastBoardSerializer(b)
    assertResult(clean("""
       |   a b c
       | 3 . .(2) 3
       | 2 2 2 2  2
       | 1 . 2 .  1
       |""")) { fbs.toLibertiesString(lastMove = b.getVertex(2, 2)) }
  }

  test("toStringIdString filled 3x3 board with 2, 2 played last") {
    val b = createFilled3x3Board()
    val fbs = new FastBoardSerializer(b)
    assertResult(clean("""
       |   a  b  c
       | 3 .  . (13) 3
       | 2 11 13 13  2
       | 1 .   7 .   1
       |""")) { fbs.toStringIdString(lastMove = b.getVertex(2, 2)) }
  }

  test("Serialize an empty 7x7 board") {
    val b = new FastBoard(7)
    val fbs = new FastBoardSerializer(b)
    assertResult(clean("""
       |   a b c d e f g
       | 7 . . . . . . .  7
       | 6 . . . . . . .  6
       | 5 . . . . . . .  5
       | 4 . . . . . . .  4
       | 3 . . . . . . .  3
       | 2 . . . . . . .  2
       | 1 . . . . . . .  1
       |   a b c d e f g
       |
       |""")) { fbs.serialize() }
  }

  test("Serialize an empty 9x9 board") {
    val b = new FastBoard(9)
    val fbs = new FastBoardSerializer(b)
    assertResult(clean("""
       |   a b c d e f g h j
       | 9 . . . . . . . . .  9
       | 8 . . . . . . . . .  8
       | 7 . . + . + . + . .  7
       | 6 . . . . . . . . .  6
       | 5 . . + . + . + . .  5
       | 4 . . . . . . . . .  4
       | 3 . . + . + . + . .  3
       | 2 . . . . . . . . .  2
       | 1 . . . . . . . . .  1
       |   a b c d e f g h j
       |
       |""")) { fbs.serialize() }
  }

  test("Serialize an empty 13x13 board") {
    val b = new FastBoard(13)
    val fbs = new FastBoardSerializer(b)
    assertResult(clean("""
       |   a b c d e f g h j k l m n
       |13 . . . . . . . . . . . . . 13
       |12 . . . . . . . . . . . . . 12
       |11 . . . . . . . . . . . . . 11
       |10 . . . + . . + . . + . . . 10
       | 9 . . . . . . . . . . . . .  9
       | 8 . . . . . . . . . . . . .  8
       | 7 . . . + . . + . . + . . .  7
       | 6 . . . . . . . . . . . . .  6
       | 5 . . . . . . . . . . . . .  5
       | 4 . . . + . . + . . + . . .  4
       | 3 . . . . . . . . . . . . .  3
       | 2 . . . . . . . . . . . . .  2
       | 1 . . . . . . . . . . . . .  1
       |   a b c d e f g h j k l m n
       |
       |""")) { fbs.serialize() }
  }

  test("moveToText") {
    val b = createFilled3x3Board()
    val fbs = new FastBoardSerializer(b)
    assertResult("B1") { fbs.moveToText(b.getVertex(1, 0)) }
    assertResult("A2") { fbs.moveToText(b.getVertex(0, 1)) }
    assertResult("pass") { fbs.moveToText(PASS) }
    assertResult("resign") { fbs.moveToText(RESIGN) }
  }

  test("textToMove") {
    val b = createFilled3x3Board()
    val fbs = new FastBoardSerializer(b)
    assertResult(b.getVertex(1, 0)) { fbs.textToMove("B1") }
    assertResult(b.getVertex(0, 1)) { fbs.textToMove("A2") }
    assertResult(PASS) { fbs.textToMove("pass") }
    assertResult(RESIGN) { fbs.textToMove("resign") }
  }

  test("moveToTextSgf") {
    val b = createFilled3x3Board()
    val fbs = new FastBoardSerializer(b)
    assertResult("bc") { fbs.moveToTextSgf(b.getVertex(1, 0)) }
    assertResult("ab") { fbs.moveToTextSgf(b.getVertex(0, 1)) }
    assertResult("ca") { fbs.moveToTextSgf(b.getVertex(2, 2)) }
    assertResult("tt") { fbs.moveToTextSgf(PASS) }
    assertResult("tt") { fbs.moveToTextSgf(RESIGN) }
  }

  /**
    * @return partially filled 3x3 board
    */
  private def createFilled3x3Board(): FastBoard = {
    val b = new FastBoard(3)
    b.updateBoardFast(1, 1, BLACK)
    b.updateBoardFast(2, 1, BLACK)
    b.updateBoardFast(0, 1, WHITE)
    b.updateBoardFast(1, 0, WHITE)
    b.updateBoardFast(2, 2, BLACK)
    b
  }
}
