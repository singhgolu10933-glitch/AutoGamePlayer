package com.autogameplayer.blockpuzzle

object Shapes {
    val catalog = listOf(
        Shape("1", listOf(Cell(0,0))),
        Shape("2H", listOf(Cell(0,0), Cell(0,1))),
        Shape("2V", listOf(Cell(0,0), Cell(1,0))),
        Shape("3H", listOf(Cell(0,0), Cell(0,1), Cell(0,2))),
        Shape("3V", listOf(Cell(0,0), Cell(1,0), Cell(2,0))),
        Shape("4H", listOf(Cell(0,0), Cell(0,1), Cell(0,2), Cell(0,3))),
        Shape("4V", listOf(Cell(0,0), Cell(1,0), Cell(2,0), Cell(3,0))),
        Shape("2x2", listOf(Cell(0,0), Cell(0,1), Cell(1,0), Cell(1,1))),
        Shape("L3", listOf(Cell(0,0), Cell(1,0), Cell(1,1))),
        Shape("L3R", listOf(Cell(0,1), Cell(1,0), Cell(1,1))),
        Shape("T4", listOf(Cell(0,0), Cell(0,1), Cell(0,2), Cell(1,1))),
        Shape("Z4", listOf(Cell(0,0), Cell(0,1), Cell(1,1), Cell(1,2))),
        Shape("5H", listOf(Cell(0,0),Cell(0,1),Cell(0,2),Cell(0,3),Cell(0,4))),
        Shape("5V", listOf(Cell(0,0),Cell(1,0),Cell(2,0),Cell(3,0),Cell(4,0))),
        Shape("3x3", (0..2).flatMap { r -> (0..2).map { c -> Cell(r,c) } })
    )
}
