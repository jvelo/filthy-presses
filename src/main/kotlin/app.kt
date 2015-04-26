package thebutton

import org.firmata4j.firmata.FirmataDevice
import java.io.IOException
import java.net.URI
import java.util.Random
import javax.websocket.*

import com.fasterxml.jackson.module.kotlin.*
import org.firmata4j.Pin
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class TickFrame(val type: String, val payload: RawTick)
data class RawTick(val participants_text: String, val tick_mac: String, val seconds_left: Float, val now_str: String)
data class Tick(val participants: Int, val counter: Int)
data class Color(val red: Int, val green: Int, val blue: Int)

ClientEndpoint class ButtonClient(private val callback: (Tick) -> Unit) {
    private val endpoint = URI("wss://wss.redditmedia.com/thebutton?h=159767ba1afa42661c518ff59d544198fc00506b&e=1430137119")
    private val container = ContainerProvider.getWebSocketContainer()
    fun connect() {
        container.connectToServer(this, this.endpoint)
    }

    OnMessage fun onMessage(message: String) {
        val mapper = jacksonObjectMapper()

        // Treat all frames as tick frames. If the frame is not a tick, it will silently fail and that's fine
        val frame = mapper.readValue(message, javaClass<TickFrame>())
        val tick = convertTick(frame.payload)

        callback(tick)
    }
}

fun convertTick(rawTick: RawTick): Tick {
    val format = NumberFormat.getNumberInstance(Locale.US)
    return Tick(
            participants = format.parse(rawTick.participants_text).toInt(),
            counter = rawTick.seconds_left.toInt()
    )
}

fun getFlair(counter: Int): Color {
    return when (counter) {
        in 1..11 -> Color(red = 255, green = 0, blue = 0)    // red
        in 11..21 -> Color(red = 255, green = 128, blue = 0) // orange
        in 21..31 -> Color(red = 255, green = 255, blue = 0) // yellow
        in 31..41 -> Color(red = 0, green = 255, blue = 0)   // green
        in 41..51 -> Color(red = 0, green = 0, blue = 255)   // blue
        in 51..60 -> Color(red = 255, green = 0, blue = 255) // purple
        else -> Color(0, 0, 0)
    }
}

fun main(args: Array<String>) {

    val device = FirmataDevice("/dev/ttyUSB0")
    val scheduler = Executors.newSingleThreadScheduledExecutor();

    try {
        device.start()
        device.ensureInitializationIsDone()

        val red = device.getPin(11)
        val blue = device.getPin(10)
        val green = device.getPin(9)

        red.setMode(Pin.Mode.PWM)
        green.setMode(Pin.Mode.PWM)
        blue.setMode(Pin.Mode.PWM)

        var previousTick = Tick(participants = -1, counter = -1)

        val client = ButtonClient({ tick ->

            if (tick.participants != previousTick.participants && previousTick.participants > 0) {
                // Counter has reset!
                val flair = getFlair(previousTick.counter)

                println("Counter reset at ${previousTick.counter} with flair ${flair}")

                red.setValue(flair.red.toLong())
                green.setValue(flair.green.toLong())
                blue.setValue(flair.blue.toLong())

                scheduler.schedule({
                    red.setValue(0)
                    blue.setValue(0)
                    green.setValue(0)
                }, 750, TimeUnit.MILLISECONDS)
            }

            previousTick = tick
        })

        client.connect()

    } catch(e: IOException) {
        println("Failed to connect to Arduino : ${e.getMessage()}")
        println("Have you tried turning it off and on again ?")
    }

}
