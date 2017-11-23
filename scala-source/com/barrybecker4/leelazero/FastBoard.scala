package com.barrybecker4.leelazero

import FastBoard._


object FastBoard {

  /** Neighbor counts are up to 4, so 3 bits is ok, but a power of 2 makes things a bit faster */
  val NBR_SHIFT = 4

  /** largest board supported */
  val MAX_BOARD_SIZE = 19

  /** highest existing square */
  val MAXSQ : Short = ((MAX_BOARD_SIZE + 2) * (MAX_BOARD_SIZE + 2)).toShort

  /** infinite score */
  val BIG = 10000000

  /** vertex of a pass */
  val PASS: Int   = -1

  /**  vertex of a "resign move" */
  val RESIGN: Int = -2

  /** possible contents of a square */    // square_t
  val BLACK: Byte = 0
  val WHITE: Byte = 1
  val EMPTY: Byte = 2
  val INVALID: Byte = 3

  type Point = Tuple2[Int, Int]
  type MoveScore = Tuple2[Int, Float]    // movescore_t

  /**  bit masks to detect eyes on neighbors */
  val EYE_MASK: Array[Int] = Array(               // s_eyemask
    4 * (1 << (NBR_SHIFT * BLACK)),
    4 * (1 << (NBR_SHIFT * WHITE))
  )

  val CINVERT = Array(WHITE, BLACK, EMPTY, INVALID) // s_cinvert
}

class FastBoard() {

  // int FastBoard::get_boardsize() return m_boardsize;

  /*
  std::array<square_t, MAXSQ>            m_square;      /* board contents */
  std::array<unsigned short, MAXSQ+1>    m_next;        /* next stone in string */

  std::array<unsigned short, MAXSQ+1>    m_parent;      /* parent node of string */
  std::array<unsigned short, MAXSQ+1>    m_libs;        /* liberties per string parent */
  std::array<unsigned short, MAXSQ+1>    m_stones;      /* stones per string parent */
  std::array<unsigned short, MAXSQ>      m_neighbors;  /* counts of neighboring stones */
  std::array<int, 4>                     m_dirs;        /* movement directions 4 way */
  std::array<int, 8>                     m_extradirs;   /* movement directions 8 way */
  std::array<int, 2>                     m_prisoners;   /* prisoners per color */
  std::array<int, 2>                     m_totalstones; /* stones per color */
  std::vector<int>                       m_critical;    /* queue of critical points */
  std::array<unsigned short, MAXSQ>      m_empty;       /* empty squares */
  std::array<unsigned short, MAXSQ>      m_empty_idx;   /* indexes of square */
  int m_empty_cnt;                                      /* count of empties */

  int m_tomove;
  int m_maxsq;*/

  private var boardSize: Int = MAX_BOARD_SIZE
  private var scoremoves_t: Seq[MoveScore] = _
  private var m_maxsq: Short = _
  private var m_square: Array[Byte] = _    // Board contents          std::array<square_t, MAXSQ>
  private var m_next: Array[Short] = _     // next stone in string   std::array<unsigned short, MAXSQ+1>
  private var m_parent: Array[Short] = _   // parent node of string
  private var m_libs: Array[Short] = _     // liberties per string parent
  private var m_stones: Array[Short] = _   // stones per string parent
  private var m_neighbors: Array[Short] = _  // counts of neighboring stones
  private var m_dirs: Array[Int] = _          // movement in 4 directions
  private var m_extradirs: Array[Int] = _     // movement in 8 directions
  private var m_prisoners: Array[Int] = _     // prisoners per color
  private var m_totalstones: Array[Int] = _       // total stones per color
  private var m_critical: Seq[Int] = Seq()              // queue of critical points  (use dropRight to pop)
  private var m_empty = Array.ofDim[Short](m_maxsq)     // empty squares
  private var m_empty_idx = Array.ofDim[Short](m_maxsq)  // indices of empty squares
  private var m_empty_cnt: Int = 0
  private var m_tomove: Byte = 0

  def getVertex(x: Int, y: Int): Short = {
    assert(x >= 0 && x < MAX_BOARD_SIZE)
    assert(y >= 0 && y < MAX_BOARD_SIZE)
    assert(x >= 0 && x < boardSize)
    assert(y >= 0 && y < boardSize)

    val vertex: Short = (((y + 1) * (boardSize + 2)) + (x + 1)).toShort
    assert(vertex >= 0 && vertex < m_maxsq)
    vertex
  }

  def getXY(vertex: Int): Point = {
    val x: Int = (vertex % (boardSize + 2)) - 1
    val y: Int = (vertex / (boardSize + 2)) - 1

    assert(x >= 0 && x < boardSize)
    assert(y >= 0 && y < boardSize)
    assert(getVertex(x, y) == vertex)
    (x, y)
  }

  def getSquare(vertex: Int): Byte = {
    assert(vertex >= 0 && vertex < m_maxsq)
    m_square(vertex)
  }

  def setSquare(vertex: Int, content: Byte): Unit = {
    assert(vertex >= 0 && vertex < m_maxsq)
    assert(content >= BLACK && content <= INVALID)
    m_square(vertex) = content
  }

  def getSquare(x: Int, y: Int): Byte = getSquare(getVertex(x, y))
  def setSquare(x: Int, y: Int, content: Byte): Unit = setSquare(getVertex(x, y), content)

  def rotateVertex(vertex: Int, symmetry: Int): Int = {
    assert(symmetry >= 0 && symmetry <= 7)
    val xy: Point = getXY(vertex)
    val x: Int = xy._1
    val y: Int = xy._2

    val newxy = symmetry match {
      case 0 => (x, y)
      case 1 => (boardSize - x - 1, y)
      case 2 => (x, boardSize - y - 1)
      case 3 => (boardSize - x - 1, boardSize - y - 1)
      case 4 => (y, x)
      case 5 => (y - 1, x)
      case 6 => (y , x - 1)
      case 7 => (boardSize - y - 1, boardSize - x - 1)
      case _ => throw new IllegalArgumentException("Unexpected symmetry value: " + symmetry)
    }

    getVertex(newxy._1, newxy._2)
  }

  def resetBoard(size: Short): Unit = {
    boardSize = size
    m_maxsq = ((size + 2) * (size + 2)).toShort

    m_square = Array.ofDim[Byte](m_maxsq)    // Board contents          std::array<square_t, MAXSQ>
    m_next = Array.ofDim[Short](m_maxsq + 1)  // next stone in string   std::array<unsigned short, MAXSQ+1>
    m_parent = Array.ofDim[Short](m_maxsq + 1)  // parent node of string
    m_libs = Array.ofDim[Short](m_maxsq + 1)  // liberties per string parent
    m_stones = Array.ofDim[Short](m_maxsq + 1)  // stones per string parent
    m_neighbors = Array.ofDim[Short](m_maxsq )  // counts of neighboring stones
    m_dirs = Array.ofDim[Int](4)              // movement in 4 directions
    m_extradirs = Array.ofDim[Int](8)         // movement in 8 directions
    m_prisoners = Array.ofDim[Int](2)         // prisoners per color
    m_totalstones = Array.ofDim[Int](2)       // total stones per color
    m_critical = Seq()              // queue of critical points  (use dropRight to pop)
    m_empty = Array.ofDim[Short](m_maxsq)     // empty squares
    m_empty_idx = Array.ofDim[Short](m_maxsq)  // indices of empty squares

    m_tomove = BLACK
    m_prisoners(BLACK) = 0
    m_prisoners(WHITE) = 0
    m_totalstones(BLACK) = 0
    m_totalstones(WHITE) = 0
    var m_empty_cnt: Short = 0

    m_dirs(0) = -size - 2
    m_dirs(1) = +1
    m_dirs(2) = +size + 2
    m_dirs(3) = -1

    m_extradirs(0) = -size - 2 - 1
    m_extradirs(1) = -size - 2
    m_extradirs(2) = -size - 2 + 1
    m_extradirs(3) = -1
    m_extradirs(4) = +1
    m_extradirs(5) = +size + 2 - 1
    m_extradirs(6) = +size + 2
    m_extradirs(7) = +size + 2 + 1

    for (i <- 0 until m_maxsq) {
      m_square(i) = INVALID
      m_neighbors(i) = 0
      m_parent(i) = MAXSQ
    }

    for (i <- 0 until size) {
      for (j <- 0 until size) {
        val vertex: Short = getVertex(i, j)

        m_square(vertex) = EMPTY
        m_empty_idx(vertex) = m_empty_cnt
        m_empty(m_empty_cnt) = vertex
        m_empty_cnt += 1

        if (i == 0 || i == size - 1) {
          m_neighbors(vertex) += (1 << (NBR_SHIFT * BLACK)) | (1 << (NBR_SHIFT * WHITE))
          m_neighbors(vertex) +=  1 << (NBR_SHIFT * EMPTY)
        } else {
          m_neighbors(vertex) +=  2 << (NBR_SHIFT * EMPTY)
        }

        if (j == 0 || j == size - 1) {
          m_neighbors(vertex) += (1 << (NBR_SHIFT * BLACK))| (1 << (NBR_SHIFT * WHITE))
          m_neighbors(vertex) +=  1 << (NBR_SHIFT * EMPTY)
        } else {
          m_neighbors(vertex) +=  2 << (NBR_SHIFT * EMPTY)
        }
      }
    }

    m_parent(MAXSQ) = MAXSQ
    m_libs(MAXSQ)   = 16384   /* subtract from this */
    m_next(MAXSQ)   = MAXSQ
  }

  def isSuicide(i: Int, color: Int): Boolean = {
    if (countPliberties(i) > 0) {
      return false
    }

    var connecting = false

    for (k <- 0 until 4) {
      val ai = i + m_dirs(k)

      val libs = m_libs(m_parent(ai))
      if (getSquare(ai) == color) {
        if (libs > 1) {
          // connecting to live group = never suicide
          return false
        }
        connecting = true
      } else {
        if (libs <= 1) {
          // killing neighbor = never suicide
          return false
        }
      }
    }

    addNeighbor(i, color)

    var opps_live = true
    var ours_die = true

    for (k <- 0 until 4) {
      val ai = i + m_dirs(k)
      val libs = m_libs(m_parent(ai))

      if (libs == 0 && getSquare(ai) != color) {
        opps_live = false
      } else if (libs != 0 && getSquare(ai) == color) {
        ours_die = false
      }
    }

    removeNeighbor(i, color)

    if (!connecting) opps_live else opps_live && ours_die
  }

  def countPliberties(i: Int): Int = {
    countNeighbors(EMPTY, i)
  }

  /**
    * @return Count of neighbors of color c at vertex v the border of the board has fake neighours of both colors
    */
  def countNeighbors(c: Int, v: Int): Int = {
    assert(c == WHITE || c == BLACK || c == EMPTY)
    (m_neighbors(v) >> (NBR_SHIFT * c)) & 7
  }

  def addNeighbor(idx: Int, color: Int): Unit = {
    assert(color == WHITE || color == BLACK || color == EMPTY)

    val nbrPars = Array[Int](4)
    var nbr_par_cnt: Int = 0

    for (k <- 0 until 4) {
      val ai = idx + m_dirs(k)
      m_neighbors(ai) += (1 << (NBR_SHIFT * color)) - (1 << (NBR_SHIFT * EMPTY))

      var found = false
      var i = 0
      while (i < nbr_par_cnt && !found) {
        if (nbrPars(i) == m_parent(ai)) {
          found = true
        }
        i += 1
      }
      if (!found) {
        m_libs(m_parent(ai)) -= 1
        nbrPars(nbr_par_cnt) = m_parent(ai)
        nbr_par_cnt += 1
      }
    }
  }

  def removeNeighbor(idx: Int, color: Int): Unit = {
    assert(color == WHITE || color == BLACK || color == EMPTY)

    val nbrPars = Array[Int](4)
    var nbr_par_cnt: Int = 0

    for (k <- 0 until 4) {
      val ai = idx + m_dirs(k)

      m_neighbors(ai) += (1 << (NBR_SHIFT * EMPTY)) - (1 << (NBR_SHIFT * color))

      var found = false
      var i = 0
      while (i < nbr_par_cnt && !found) {
        if (nbrPars(i) == m_parent(ai)) {
          found = true
        }
      }
      if (!found) {
        m_libs(m_parent(ai)) += 1
        nbrPars(nbr_par_cnt) = m_parent(ai)
        nbr_par_cnt += 1
      }
    }
  }

  def otherColor(color: Int): Int =
    if (color == BLACK) WHITE
    else if (color == WHITE) BLACK
    else throw new IllegalStateException("Unexpected color: " + color)

  def fastSsSuicide(color: Int, i: Int): Boolean = {
    val eyeplay: Int = m_neighbors(i) & EYE_MASK(otherColor(color))

    if (eyeplay > 0) return false

    !((m_libs(m_parent(i - 1)) <= 1) ||
      (m_libs(m_parent(i + 1)) <= 1) ||
      (m_libs(m_parent(i + boardSize + 2)) <= 1) ||
      (m_libs(m_parent(i - boardSize - 2)) <= 1))
  }
  /*

  int FastBoard::remove_string_fast(int i) {
    int pos = i;
    int removed = 0;
    int color = m_square[i];

    assert(color == WHITE || color == BLACK || color == EMPTY);

    do {
      assert(m_square[pos] == color);

      m_square[pos]  = EMPTY;
      m_parent[pos]  = MAXSQ;
      m_totalstones[color]--;

      remove_neighbor(pos, color);

      m_empty_idx[pos]     = m_empty_cnt;
      m_empty[m_empty_cnt] = pos;
      m_empty_cnt++;

      removed++;
      pos = m_next[pos];
    } while (pos != i);

    return removed;
  }

  std::vector<bool> FastBoard::calc_reach_color(int col) {
    auto bd = std::vector<bool>(m_maxsq);
    auto last = std::vector<bool>(m_maxsq);

    std::fill(begin(bd), end(bd), false);
    std::fill(begin(last), end(last), false);

    /* needs multi pass propagation, slow */
    do {
      last = bd;
      for (int i = 0; i < m_boardsize; i++) {
        for (int j = 0; j < m_boardsize; j++) {
          int vertex = get_vertex(i, j);
          /* colored field, spread */
          if (m_square[vertex] == col) {
            bd[vertex] = true;
            for (int k = 0; k < 4; k++) {
              if (m_square[vertex + m_dirs[k]] == EMPTY) {
                bd[vertex + m_dirs[k]] = true;
              }
            }
          } else if (m_square[vertex] == EMPTY && bd[vertex]) {
            for (int k = 0; k < 4; k++) {
              if (m_square[vertex + m_dirs[k]] == EMPTY) {
                bd[vertex + m_dirs[k]] = true;
              }
            }
          }
        }
      }
    } while (last != bd);

    return bd;
  }

  // Needed for scoring passed out games not in MC playouts
  float FastBoard::area_score(float komi) {
    auto white = calc_reach_color(WHITE);
    auto black = calc_reach_color(BLACK);

    auto score = -komi;

    for (int i = 0; i < m_boardsize; i++) {
      for (int j = 0; j < m_boardsize; j++) {
        auto vertex = get_vertex(i, j);

        if (white[vertex] && !black[vertex]) {
          score -= 1.0f;
        } else if (black[vertex] && !white[vertex]) {
          score += 1.0f;
        }
      }
    }

    return score;
  }

  int FastBoard::get_stone_count() {
    return m_totalstones[BLACK] + m_totalstones[WHITE];
  }

  int FastBoard::estimate_mc_score(float komi) {
    int wsc, bsc;

    bsc = m_totalstones[BLACK];
    wsc = m_totalstones[WHITE];

    return bsc-wsc-((int)komi)+1;
  }

  float FastBoard::final_mc_score(float komi) {
    int wsc, bsc;
    int maxempty = m_empty_cnt;

    bsc = m_totalstones[BLACK];
    wsc = m_totalstones[WHITE];

    for (int v = 0; v < maxempty; v++) {
      int i = m_empty[v];

      assert(m_square[i] == EMPTY);

      int allblack = ((m_neighbors[i] >> (NBR_SHIFT * BLACK)) & 7) == 4;
      int allwhite = ((m_neighbors[i] >> (NBR_SHIFT * WHITE)) & 7) == 4;

      if (allwhite) {
        wsc++;
      } else if (allblack) {
        bsc++;
      }
    }

    return (float)(bsc)-((float)(wsc)+komi);
  }

  void FastBoard::display_board(int lastmove) {
    int boardsize = get_boardsize();

    myprintf("\n   ");
    for (int i = 0; i < boardsize; i++) {
      if (i < 25) {
        myprintf("%c ", (('a' + i < 'i') ? 'a' + i : 'a' + i + 1));
      } else {
        myprintf("%c ", (('A' + (i-25) < 'I') ? 'A' + (i-25) : 'A' + (i-25) + 1));
      }
    }
    myprintf("\n");
    for (int j = boardsize-1; j >= 0; j--) {
      myprintf("%2d", j+1);
      if (lastmove == get_vertex(0, j))
        myprintf("(");
      else
        myprintf(" ");
      for (int i = 0; i < boardsize; i++) {
        if (get_square(i,j) == WHITE) {
          myprintf("O");
        } else if (get_square(i,j) == BLACK)  {
          myprintf("X");
        } else if (starpoint(boardsize, i, j)) {
          myprintf("+");
        } else {
          myprintf(".");
        }
        if (lastmove == get_vertex(i, j)) myprintf(")");
        else if (i != boardsize-1 && lastmove == get_vertex(i, j)+1) myprintf("(");
        else myprintf(" ");
      }
      myprintf("%2d\n", j+1);
    }
    myprintf("   ");
    for (int i = 0; i < boardsize; i++) {
      if (i < 25) {
        myprintf("%c ", (('a' + i < 'i') ? 'a' + i : 'a' + i + 1));
      } else {
        myprintf("%c ", (('A' + (i-25) < 'I') ? 'A' + (i-25) : 'A' + (i-25) + 1));
      }
    }
    myprintf("\n\n");
  }

  void FastBoard::display_liberties(int lastmove) {
    int boardsize = get_boardsize();

    myprintf("   ");
    for (int i = 0; i < boardsize; i++) {
      myprintf("%c ", (('a' + i < 'i') ? 'a' + i : 'a' + i + 1));
    }
    myprintf("\n");
    for (int j = boardsize-1; j >= 0; j--) {
      myprintf("%2d", j+1);
      if (lastmove == get_vertex(0,j) )
        myprintf("(");
      else
        myprintf(" ");
      for (int i = 0; i < boardsize; i++) {
        if (get_square(i,j) == WHITE) {
          int libs = m_libs[m_parent[get_vertex(i,j)]];
          if (libs > 9) { libs = 9; };
          myprintf("%1d", libs);
        } else if (get_square(i,j) == BLACK)  {
          int libs = m_libs[m_parent[get_vertex(i,j)]];
          if (libs > 9) { libs = 9; };
          myprintf("%1d", libs);
        } else if (starpoint(boardsize, i, j)) {
          myprintf("+");
        } else {
          myprintf(".");
        }
        if (lastmove == get_vertex(i, j)) myprintf(")");
        else if (i != boardsize-1 && lastmove == get_vertex(i, j)+1) myprintf("(");
        else myprintf(" ");
      }
      myprintf("%2d\n", j+1);
    }
    myprintf("\n\n");

    myprintf("   ");
    for (int i = 0; i < boardsize; i++) {
      myprintf("%c ", (('a' + i < 'i') ? 'a' + i : 'a' + i + 1));
    }
    myprintf("\n");
    for (int j = boardsize-1; j >= 0; j--) {
      myprintf("%2d", j+1);
      if (lastmove == get_vertex(0,j) )
        myprintf("(");
      else
        myprintf(" ");
      for (int i = 0; i < boardsize; i++) {
        if (get_square(i,j) == WHITE) {
          int id = m_parent[get_vertex(i,j)];
          myprintf("%2d", id);
        } else if (get_square(i,j) == BLACK)  {
          int id = m_parent[get_vertex(i,j)];
          myprintf("%2d", id);
        } else if (starpoint(boardsize, i, j)) {
          myprintf("+ ");
        } else {
          myprintf(". ");
        }
        if (lastmove == get_vertex(i, j)) myprintf(")");
        else if (i != boardsize-1 && lastmove == get_vertex(i, j)+1) myprintf("(");
        else myprintf(" ");
      }
      myprintf("%2d\n", j+1);
    }
    myprintf("\n\n");
  }

  void FastBoard::merge_strings(const int ip, const int aip) {
    assert(ip != MAXSQ && aip != MAXSQ);

    /* merge stones */
    m_stones[ip] += m_stones[aip];

    /* loop over stones, update parents */
    int newpos = aip;

    do {
      // check if this stone has a liberty
      for (int k = 0; k < 4; k++) {
        int ai = newpos + m_dirs[k];
        // for each liberty, check if it is not shared
        if (m_square[ai] == EMPTY) {
          // find liberty neighbors
          bool found = false;
          for (int kk = 0; kk < 4; kk++) {
            int aai = ai + m_dirs[kk];
            // friendly string shouldn't be ip
            // ip can also be an aip that has been marked
            if (m_parent[aai] == ip) {
              found = true;
              break;
            }
          }

          if (!found) {
            m_libs[ip]++;
          }
        }
      }

      m_parent[newpos] = ip;
      newpos = m_next[newpos];
    } while (newpos != aip);

    /* merge stings */
    int tmp = m_next[aip];
    m_next[aip] = m_next[ip];
    m_next[ip] = tmp;
  }

  int FastBoard::update_board_eye(const int color, const int i) {
    m_square[i]  = (square_t)color;
    m_next[i]    = i;
    m_parent[i]  = i;
    m_libs[i]    = 0;
    m_stones[i]  = 1;
    m_totalstones[color]++;

    add_neighbor(i, color);

    int captured_sq;
    int captured_stones = 0;

    for (int k = 0; k < 4; k++) {
      int ai = i + m_dirs[k];

      assert(ai >= 0 && ai <= m_maxsq);

      if (m_libs[m_parent[ai]] <= 0) {
        int this_captured    = remove_string_fast(ai);
        captured_sq          = ai;
        captured_stones     += this_captured;
      }
    }

    /* move last vertex in list to our position */
    int lastvertex               = m_empty[--m_empty_cnt];
    m_empty_idx[lastvertex]      = m_empty_idx[i];
    m_empty[m_empty_idx[i]]      = lastvertex;

    m_prisoners[color] += captured_stones;

    // possibility of ko
    if (captured_stones == 1) {
      return captured_sq;
    }

    return -1;
  }

  /*
      returns ko square or suicide tag
      does not update side to move
  */
  int FastBoard::update_board_fast(const int color, const int i, bool & capture) {
    assert(m_square[i] == EMPTY);
    assert(color == WHITE || color == BLACK);

    /* did we play into an opponent eye? */
    int eyeplay = (m_neighbors[i] & s_eyemask[!color]);

    // because we check for single stone suicide, we know
    // its a capture, and it might be a ko capture
    if (eyeplay) {
      capture = true;
      return update_board_eye(color, i);
    }

    m_square[i]  = (square_t)color;
    m_next[i]    = i;
    m_parent[i]  = i;
    m_libs[i]    = count_pliberties(i);
    m_stones[i]  = 1;
    m_totalstones[color]++;

    add_neighbor(i, color);

    for (int k = 0; k < 4; k++) {
      int ai = i + m_dirs[k];

      if (m_square[ai] > WHITE) continue;

      assert(ai >= 0 && ai <= m_maxsq);

      if (m_square[ai] == !color) {
        if (m_libs[m_parent[ai]] <= 0) {
          capture = true;
          m_prisoners[color] += remove_string_fast(ai);
        }
      } else if (m_square[ai] == color) {
        int ip  = m_parent[i];
        int aip = m_parent[ai];

        if (ip != aip) {
          if (m_stones[ip] >= m_stones[aip]) {
            merge_strings(ip, aip);
          } else {
            merge_strings(aip, ip);
          }
        }
      }
    }

    /* move last vertex in list to our position */
    int lastvertex               = m_empty[--m_empty_cnt];
    m_empty_idx[lastvertex]      = m_empty_idx[i];
    m_empty[m_empty_idx[i]]      = lastvertex;

    assert(m_libs[m_parent[i]] < m_boardsize*m_boardsize);

    /* check whether we still live (i.e. detect suicide) */
    if (m_libs[m_parent[i]] == 0) {
      remove_string_fast(i);
    }

    return -1;
  }

  bool FastBoard::is_eye(const int color, const int i) {
    /* check for 4 neighbors of the same color */
    int ownsurrounded = (m_neighbors[i] & s_eyemask[color]);

    // if not, it can't be an eye
    // this takes advantage of borders being colored
    // both ways
    if (!ownsurrounded) {
      return false;
    }

    // 2 or more diagonals taken
    // 1 for side groups
    int colorcount[4];

    colorcount[BLACK] = 0;
    colorcount[WHITE] = 0;
    colorcount[INVAL] = 0;

    colorcount[m_square[i - 1 - m_boardsize - 2]]++;
    colorcount[m_square[i + 1 - m_boardsize - 2]]++;
    colorcount[m_square[i - 1 + m_boardsize + 2]]++;
    colorcount[m_square[i + 1 + m_boardsize + 2]]++;

    if (colorcount[INVAL] == 0) {
      if (colorcount[!color] > 1) {
        return false;
      }
    } else {
      if (colorcount[!color]) {
        return false;
      }
    }

    return true;
  }

  std::string FastBoard::move_to_text(int move) {
    std::ostringstream result;

    int column = move % (m_boardsize + 2);
    int row = move / (m_boardsize + 2);

    column--;
    row--;

    assert(move == FastBoard::PASS || move == FastBoard::RESIGN || (row >= 0 && row < m_boardsize));
    assert(move == FastBoard::PASS || move == FastBoard::RESIGN || (column >= 0 && column < m_boardsize));

    if (move >= 0 && move <= m_maxsq) {
      result << static_cast<char>(column < 8 ? 'A' + column : 'A' + column + 1);
      result << (row + 1);
    } else if (move == FastBoard::PASS) {
      result << "pass";
    } else if (move == FastBoard::RESIGN) {
      result << "resign";
    } else {
      result << "error";
    }

    return result.str();
  }

  std::string FastBoard::move_to_text_sgf(int move) {
    std::ostringstream result;

    int column = move % (m_boardsize + 2);
    int row = move / (m_boardsize + 2);

    column--;
    row--;

    assert(move == FastBoard::PASS || move == FastBoard::RESIGN || (row >= 0 && row < m_boardsize));
    assert(move == FastBoard::PASS || move == FastBoard::RESIGN || (column >= 0 && column < m_boardsize));

    // SGF inverts rows
    row = m_boardsize - row - 1;

    if (move >= 0 && move <= m_maxsq) {
      if (column <= 25) {
        result << static_cast<char>('a' + column);
      } else {
        result << static_cast<char>('A' + column - 26);
      }
      if (row <= 25) {
        result << static_cast<char>('a' + row);
      } else {
        result << static_cast<char>('A' + row - 26);
      }
    } else if (move == FastBoard::PASS) {
      result << "tt";
    } else if (move == FastBoard::RESIGN) {
      result << "tt";
    } else {
      result << "error";
    }

    return result.str();
  }

  int FastBoard::text_to_move(std::string move) {
    if (move.size() == 0 || move == "pass") {
      return FastBoard::PASS;
    }
    if (move == "resign") {
      return FastBoard::RESIGN;
    }

    char c1 = tolower(move[0]);
    int x = c1 - 'a';
    // There is no i in ...
    assert(x != 8);
    if (x > 8) x--;
    std::string remainder = move.substr(1);
    int y = std::stoi(remainder) - 1;

    int vtx = get_vertex(x, y);

    return vtx;
  }

  bool FastBoard::starpoint(int size, int point) {
    int stars[3];
    int points[2];
    int hits = 0;

    if (size % 2 == 0 || size < 9) {
      return false;
    }

    stars[0] = size >= 13 ? 3 : 2;
    stars[1] = size / 2;
    stars[2] = size - 1 - stars[0];

    points[0] = point / size;
    points[1] = point % size;

    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 3; j++) {
        if (points[i] == stars[j]) {
          hits++;
        }
      }
    }

    return hits >= 2;
  }

  bool FastBoard::starpoint(int size, int x, int y) {
    return starpoint(size, y * size + x);
  }

  int FastBoard::get_prisoners(int side) {
    assert(side == WHITE || side == BLACK);

    return m_prisoners[side];
  }

  bool FastBoard::black_to_move() {
    return m_tomove == BLACK;
  }

  int FastBoard::get_to_move() {
    return m_tomove;
  }

  void FastBoard::set_to_move(int tomove) {
    m_tomove = tomove;
  }

  int FastBoard::get_groupid(int vertex) {
    assert(m_square[vertex] == WHITE || m_square[vertex] == BLACK);
    assert(m_parent[vertex] == m_parent[m_parent[vertex]]);

    return m_parent[vertex];
  }

  std::vector<int> FastBoard::get_string_stones(int vertex) {
    int start = m_parent[vertex];

    std::vector<int> res;
    res.reserve(m_stones[start]);

    int newpos = start;

    do {
      assert(m_square[newpos] == m_square[vertex]);
      res.push_back(newpos);
      newpos = m_next[newpos];
    } while (newpos != start);

    return res;
  }

  std::string FastBoard::get_string(int vertex) {
    std::string result;

    int start = m_parent[vertex];
    int newpos = start;

    do {
      result += move_to_text(newpos) + " ";
      newpos = m_next[newpos];
    } while (newpos != start);

    // eat last space
    result.resize(result.size() - 1);

    return result;
  }

  bool FastBoard::fast_in_atari(int vertex) {
    assert((m_square[vertex] < EMPTY) || (m_libs[m_parent[vertex]] > MAXSQ));

    int par = m_parent[vertex];
    int lib = m_libs[par];

    return lib == 1;
  }

  // check if string is in atari, returns 0 if not,
  // single liberty if it is
  int FastBoard::in_atari(int vertex) {
    assert(m_square[vertex] < EMPTY);

    if (m_libs[m_parent[vertex]] > 1) {
      return false;
    }

    assert(m_libs[m_parent[vertex]] == 1);

    int pos = vertex;

    do {
      if (count_pliberties(pos)) {
        for (int k = 0; k < 4; k++) {
          int ai = pos + m_dirs[k];
          if (m_square[ai] == EMPTY) {
            return ai;
          }
        }
      }

      pos = m_next[pos];
    } while (pos != vertex);

    assert(false);

    return false;
  }
  int FastBoard::get_dir(int i) {
    return m_dirs[i];
  }

  int FastBoard::get_extra_dir(int i) {
    return m_extradirs[i];
  }

  std::string FastBoard::get_stone_list() {
    std::string res;

    for (int i = 0; i < m_boardsize; i++) {
      for (int j = 0; j < m_boardsize; j++) {
        int vertex = get_vertex(i, j);

        if (get_square(vertex) != EMPTY) {
          res += move_to_text(vertex) + " ";
        }
      }
    }

    // eat final space
    res.resize(res.size() - 1);

    return res;
  }

  int FastBoard::string_size(int vertex) {
    assert(vertex > 0 && vertex < m_maxsq);
    assert(m_square[vertex] == WHITE || m_square[vertex] == BLACK);

    return m_stones[m_parent[vertex]];
  }

  int FastBoard::count_rliberties(int vertex) {
    /*std::vector<bool> marker(m_maxsq, false);

    int pos = vertex;
    int liberties = 0;
    int color = m_square[vertex];

    assert(color == WHITE || color == BLACK);

    do {
        assert(m_square[pos] == color);

        for (int k = 0; k < 4; k++) {
            int ai = pos + m_dirs[k];
            if (m_square[ai] == EMPTY) {
                if (!marker[ai]) {
                    liberties++;
                    marker[ai] = true;
                }
            }
        }
        pos = m_next[pos];
    } while (pos != vertex);

    return liberties;*/
    return m_libs[m_parent[vertex]];
  }

  int FastBoard::merged_string_size(int color, int vertex) {
    int totalsize = 0;
    std::array<int, 4> nbrpar;
    int nbrcnt = 0;

    for (int k = 0; k < 4; k++) {
      int ai = vertex + m_dirs[k];

      if (get_square(ai) == color) {
        int par = m_parent[ai];

        bool found = false;
        for (int i = 0; i < nbrcnt; i++) {
          if (nbrpar[i] == par) {
            found = true;
            break;
          }
        }

        if (!found) {
          totalsize += string_size(ai);
          nbrpar[nbrcnt++] = par;
        }
      }

    }

    return totalsize;
  }
*/
}
