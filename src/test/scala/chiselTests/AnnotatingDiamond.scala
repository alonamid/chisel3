// See LICENSE for license details.

package chiselTests

import chisel3._
import chisel3.core.{UInt, Annotation, Module}
import chisel3.internal.firrtl.Emitter
import chisel3.testers.BasicTester
import org.scalatest._

//scalastyle:off magic.number

/** A diamond circuit Top instantiates A and B and both A and B instantiate C
  * Illustrations of annotations of various components and modules in both
  * relative and absolute cases
  *
  * This is currently not much of a test, read the printout to see what annotations look like
  */
case class MyRelativeAnno(value: String) extends Annotation.Value with Annotation.Relative
case class AbsoluteAnno(value: String) extends Annotation.Value with Annotation.Absolute

class ModC(param1: Int, param2: Int) extends Module {
  val io = new Bundle {
    val in = UInt(INPUT)
    val out = UInt(OUTPUT)
  }
  io.out := io.in

  annotate(this, AbsoluteAnno(s"ModC Absolute"))
  annotate(this, MyRelativeAnno(s"ModC Relative"))

  annotate(io.in, AbsoluteAnno(s"ModuleC($param1,$param2) width < $param1"))
  annotate(io.out, MyRelativeAnno(s"ModuleC($param1,$param2) width < $param2"))
  println(s"ModuleName: ModC is ${this.getClass.getName}")
}

class ModA(aParam1: Int, aParam2: Int) extends Module {
  val io = new Bundle {
    val in = UInt().flip()
    val out = UInt()
  }
  val modC = Module(new ModC(1, 2))
  modC.io.in := io.in
  io.out := modC.io.out

  annotate(this, AbsoluteAnno(s"ModA Absolute"))
  annotate(this, MyRelativeAnno(s"ModA Relative"))

  annotate(io.in, AbsoluteAnno(s"ModuleA($aParam1,$aParam2) width < $aParam1"))
  annotate(io.out, MyRelativeAnno(s"ModuleA($aParam1,$aParam2) width < $aParam2"))
  println(s"ModuleName: ModA is ${this.getClass.getName}")
}

class ModB(bParam1: Int, bParam2: Int) extends Module {
  val io = new Bundle {
    val in = UInt().flip()
    val out = UInt()
  }
  val modC = Module(new ModC(1, 2))
  modC.io.in := io.in
  io.out := modC.io.out
  annotate(io.in, AbsoluteAnno(s"ModuleB($bParam1,$bParam2) width < $bParam1"))
  annotate(io.out, MyRelativeAnno(s"ModuleB($bParam1,$bParam2) width < $bParam2"))
  annotate(modC.io.in, AbsoluteAnno(s"ModuleB.c.io.in absolute"))
  annotate(modC.io.in, MyRelativeAnno(s"ModuleB.c.io.in relative"))
  println(s"ModuleName: ModA is ${this.getClass.getName}")
}

class TopOfDiamond extends Module {
  val io = new Bundle {
    val in   = UInt(INPUT, 32)
    val out  = UInt(INPUT, 32)
  }
  val x = Reg(UInt(width = 32))
  val y = Reg(UInt(width = 32))

  val modA = Module(new ModA(1, 2))
  val modB = Module(new ModB(3, 4))

  x := io.in
  modA.io.in := x
  modB.io.in := x

  y := modA.io.out + modB.io.out
  io.out := y

  annotate(this, AbsoluteAnno(s"TopOfDiamond Absolute"))
  annotate(this, MyRelativeAnno(s"TopOfDiamond Relative"))

  annotate(modB.io.in, AbsoluteAnno(s"TopOfDiamond.moduleB.io.in"))
  annotate(modB.io.in, MyRelativeAnno(s"TopOfDiamond.moduleB.io.in"))
  println(s"ModuleName: Top is ${this.getClass.getName}")

}

class DiamondTester extends BasicTester {
  val dut = Module(new TopOfDiamond)

  stop()
}

class DiamondSpec extends FlatSpec with Matchers {
  behavior of "Annotating components of a circuit"

  def hasComponent(name: String, annotations: Seq[Annotation.Resolved]): Boolean = {
    annotations.exists { annotation =>
      annotation.componentName == name }
  }
  def valueOf(name: String, annotations: Seq[Annotation.Resolved]): Option[String] = {
    annotations.find { annotation => annotation.componentName == name } match {
      case Some(Annotation.Resolved(_, AbsoluteAnno(value))) => Some(value)
      case Some(Annotation.Resolved(_, MyRelativeAnno(value))) => Some(value)
      case _ => None
    }
  }

  def show(annotations: Seq[Annotation.Resolved]): Unit = {
    println("All annotations\n")
    val (component, signalName, parentModName, pathName, parentPathName) =
      ("component", "signalName", "parentModName", "pathName", "parentPathName")

    val lines = annotations.map { a =>
      f"${a.componentName}%60s -- ${a.value}"
    }
    println(lines.toSeq.sorted.mkString("\n"))

  }

  it should "contain the following relative keys" in {
    val circuit = Driver.elaborate { () => new DiamondTester }
    val annotations = circuit.annotations

    show(annotations)
//    println(Emitter.emit(circuit))
  }
}