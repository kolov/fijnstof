package nl.kransen.fijnstof

import java.io.{InputStream, OutputStream}

import akka.actor.{Actor, ActorRef, Props}
import nl.kransen.fijnstof.Mhz19Actor.Tick
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class CO2Measurement(ppm: Int) {
  val str: String = ppm.toString
}

object CO2Measurement {
  def average(ms: List[CO2Measurement]): CO2Measurement = {
    val ppmSum = ms.foldRight(0)((nxt, sum) => nxt.ppm + sum)
    CO2Measurement(ppmSum / ms.size)
  }
}

object Mhz19Actor {
  case object Tick

  def props(in: InputStream, out: OutputStream, co2Listeners: Seq[ActorRef])(implicit ec: ExecutionContext): Props = Props(new Mhz19Actor(in, out, co2Listeners))
}

class Mhz19Actor(in: InputStream, out: OutputStream, co2Listeners: Seq[ActorRef])(implicit ec: ExecutionContext) extends Actor {

  private val log = LoggerFactory.getLogger("Mhz19Actor")

  override def receive: Receive = {
    case Tick =>
      tick()
  }

  override def preStart(): Unit = {
    context.system.dispatcher.execute(() => keepReading(in))
    tick()
  }

  @tailrec
  private def keepReading(in: InputStream): Nothing = {
    val b0: Int = in.read
    if (b0 == 0xff) {
      val b1 = in.read
      if (b1 == 0x86) {
        val b2 = in.read
        val b3 = in.read
        val ppm = (b2 * 256) + b3
        val b4 = in.read
        val b5 = in.read
        val b6 = in.read
        val b7 = in.read
        val expectedChecksum = (0xff - b1 - b2 - b3 - b4 - b5 - b6 - b7 + 1) & 0xff
        val b8 = in.read
        if (b8 == expectedChecksum) {
          val co2 = CO2Measurement(ppm)
          log.debug(s"co2: ${co2.str}")
          co2Listeners.foreach(_ ! co2)
        } else {
          log.debug(s"Checksum, expected: $expectedChecksum, actual: $b8")
        }
      }
    }
    keepReading(in)
  }

  private val readCommand = Array(0xff, 0x01, 0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79).map(_.toByte)

  private val calibrateZeroCommand = Array(0Xff, 0x01, 0x87, 0x00, 0x00, 0x00, 0x00, 0x00, 0x78).map(_.toByte)

  private def tick() = {
//    out.write(calibrateZeroCommand)
    out.write(readCommand)
    context.system.scheduler.scheduleOnce(3 seconds, self, Tick)
  }

}
