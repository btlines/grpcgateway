import com.softwaremill.sttp._
import org.scalatest.{BeforeAndAfterAll, EitherValues, FlatSpec, Matchers}

class GatewayGeneratorTest extends FlatSpec with Matchers with BeforeAndAfterAll with EitherValues {

  val gwPort = 9010

  val serverContext = new ServerContext(9005, gwPort)

  implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  override protected def beforeAll(): Unit = {
    serverContext.start()
  }

  override protected def afterAll(): Unit = {
    serverContext.shutdown()
    backend.close()
  }

  "gateway" should "handle get request to concatWords" in {

    val request = sttp.get(uri"http://localhost:$gwPort/api/concatWords?word1=one&word2=two")

    val actual = request.send()

    actual.body shouldBe 'right

    actual.body.right.value shouldBe """{"result":"onetwo"}"""

  }

  "gateway" should "handle post request to concatWords" in {

    val request = sttp.post(uri"http://localhost:$gwPort/api/concatWords2").body("""{"word1":"one","word2":"two"}""")

    val actual = request.send()

    actual.body shouldBe 'right

    actual.body.right.value shouldBe """{"result":"onetwo"}"""

  }




}









