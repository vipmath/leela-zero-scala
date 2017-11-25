package leelazero

import leelazero.FastBoard.{BLACK, WHITE}
import leelazero.TestUtil.clean
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

  /**
    * @return
    */
  private def createFilled3x3Board(): FastBoard = {
    val b = new FastBoard(3)
    b.updateBoardFast(BLACK, b.getVertex(1, 1))
    b.updateBoardFast(BLACK, b.getVertex(2, 1))
    b.updateBoardFast(WHITE, b.getVertex(0, 1))
    b.updateBoardFast(WHITE, b.getVertex(1, 0))
    b.updateBoardFast(BLACK, b.getVertex(2, 2))
    b
  }
}