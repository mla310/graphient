package graphient.specs

import cats.effect.{Fiber, IO}
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import graphient._
import graphient.Implicits._
import io.circe.Encoder
import io.circe.generic.semiauto._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class ClientSpec extends FunSpec with Matchers with BeforeAndAfterAll {

  case class GetUserPayload(userId: Long)

  object GetUserPayload {
    implicit val gupEncoder: Encoder[GetUserPayload] = deriveEncoder[GetUserPayload]
  }

  implicit val contextShift = IO.contextShift(global)
  implicit val timer        = IO.timer(global)
  implicit val backend      = AsyncHttpClientCatsBackend[IO]()

  var serverThread = None: Option[Fiber[IO, Unit]]

  override def beforeAll() = {
    serverThread = Some(TestServer.run.start.unsafeRunSync)

    while (!isServerUp()) {
      Thread.sleep(1000)
    }
  }

  override def afterAll() = {
    serverThread.foreach(_.cancel.unsafeRunSync)
  }

  private def isServerUp(): Boolean = {
    Try(sttp.get(uri"http://localhost:8080/status").send().unsafeRunSync().code == 200).getOrElse(false)
  }

  describe("Client - server integration suite") {

    case class EmptyResponse()

    implicit val emptyResponseDecoder = deriveDecoder[EmptyResponse]

    case class GetLongResponse(getLong: Long)

    implicit val getLongDecoder = deriveDecoder[GetLongResponse]

    val client = new GraphientClient[IO](TestSchema.schema, uri"http://localhost:8080/graphql")

    it("querying through the client") {
      val response: Response[String] =
        client.call(Query(TestSchema.Queries.getUser), Params("userId" -> 1L)).flatMap(_.send()).unsafeRunSync()

      response.code shouldBe 200
    }

    it("querying through the client for no arguments and scalar output") {
      val response: Response[String] =
        client.call(Query(TestSchema.Queries.getLong), Params()).flatMap(_.send()).unsafeRunSync()

      response.code shouldBe 200
      response.body shouldBe 'right
      response.body.right.get shouldBe "{\"data\":{\"getLong\":420}}"
    }

    it("decoding to response") {
      val response =
        client.callAndDecode[Params.T, GetLongResponse](Query(TestSchema.Queries.getLong), Params()).unsafeRunSync()

      response shouldBe 'right
      response.right.get.getLong shouldBe 420
    }

    it("decoding error response") {
      val response =
        client.callAndDecode[Params.T, GetLongResponse](Query(TestSchema.Queries.raiseError), Params()).unsafeRunSync()

      response shouldBe 'left

      val errors = response.left.get

      errors should have length 1

      val onlyError = errors.head

      onlyError.message shouldBe "Internal server error"

      response.left.get.head.path should contain theSameElementsInOrderAs List("raiseError")
    }

    // TODO: Check for errors
    it("mutating through the client") {
      val parameters = Params(
        "name" -> "test user",
        "age"  -> Some(26),
        "address" -> Params(
          "zip"    -> 2300,
          "city"   -> "cph",
          "street" -> "etv"
        ),
        "hobbies" -> List("coding", "debugging")
      )
      val response =
        client
          .callAndDecode[Params.T, EmptyResponse](Mutation(TestSchema.Mutations.createUser), parameters)
          .unsafeRunSync()

      response shouldBe 'right
    }

  }

}
