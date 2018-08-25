import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import net.ceedubs.ficus.Ficus._

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import scala.reflect.io.File

object Main extends App {

  private val log = LoggerFactory.getLogger("Main")

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  lazy val machineId: Option[String] = {
    val serialRegex = "Serial\\s*\\:\\s*0*([^0][0-9a-fA-F]+)".r
    for {
      cpuinfo <- Try(File("/proc/cpuinfo").slurp()).toOption
      firstMatch <- serialRegex.findFirstMatchIn(cpuinfo)
    } yield "fijnstof-" + firstMatch.group(1)
  }

  def runFlow(isTest: Boolean)(config: Config): Unit = {
    val uartDevice = config.getString("device")
    log.info(s"Connecting to UART (Serial) device: $uartDevice")

    val targets: Seq[ActorRef] = Seq(
      config.as[Option[Config]]("domoticz").map(Domoticz.props(_)).map(system.actorOf(_, "domoticz")),
      config.as[Option[Config]]("luftdaten").map(Luftdaten.props(_)).map(system.actorOf(_, "luftdaten"))
    ).collect { case Some(target) => target }

    def handleMeasurement(measurement: Measurement): Unit = {
      log.debug(s"Measurement: ${measurement.toString}")
      targets.foreach(_ ! measurement)
    }

    val sourceType = config.getString("type")
    val interval = config.as[Option[Int]]("interval").getOrElse(90)
    val batchSize = config.as[Option[Int]]("batchSize").getOrElse(interval)

    Serial.findPort(uartDevice) match {
      case Some(port) =>
        val source = if (sourceType.equalsIgnoreCase("sds011")) {
          Sds011Actor.props(port.getInputStream)
        } else if (sourceType.equalsIgnoreCase("mhz19")) {
          Mhz19Actor.props(port.getInputStream, port.getOutputStream)
        }
      case None => log.error("Serial device not found")
    }
  }

  log.info(s"Starting fijnstof, machine id: $machineId")

  if (args.contains("list")) {
    Serial.listPorts.foreach(port => log.info(s"Serial port: ${port.getName}"))
  } else {
    val isTest = args.contains("test")
    ConfigFactory.load().getConfigList("devices").forEach(runFlow(isTest))
  }

  system.terminate()
}
