package gateway

import com.softwaremill.sttp._
import org.json4s.JValue
import org.json4s.JsonAST.{JInt, JObject}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods
import org.scalatest.{EitherValues, FreeSpec, Matchers}

class ConnectionTest extends FreeSpec with EitherValues with Matchers {

  implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  def doPost(uri: Uri, body: JValue): Either[String, (Int, String)] = {
    val request = sttp.post(uri).body(JsonMethods.compact(body))

    val resp = request.send()

    resp.body.map(body => {
      resp.code -> body
    })
  }

  "ServicesRest should work" - {

    "calculate sum" in {

      val req = JObject(
        "a" -> 5,
        "b" -> 18
      )

      val resp = doPost(uri"http://localhost:9097/calcService/sum", req)

      resp shouldBe Right

      resp.right.value shouldBe (200 -> JsonMethods.compact(JObject("result" -> JInt(23))))

    }

  }

}
