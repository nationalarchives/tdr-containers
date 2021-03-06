package uk.gov.tna.tdr.graphql

import com.softwaremill.sttp.json4s._
import com.softwaremill.sttp.{HttpURLConnectionBackend, Id, SttpBackend, sttp, _}
import com.typesafe.scalalogging.Logger
import org.json4s.native.Serialization
import uk.gov.tna.tdr.auth.TokenAuth
import uk.gov.tna.tdr.checksum.ChecksumCheckResult
import uk.gov.tna.tdr.fileformat.{Files, Matches, Response}
import uk.gov.tna.tdr.viruscheck.VirusCheckResult

trait GraphqlSender[A] {
  def sendToGraphqlServer(value: A): Unit
}

object GraphqlSenderInstances {
  case class GraphqlQuery(query: String)
  val logger = Logger("Graphql")

  def sendQuery(query: String): Unit = {
    val tokenAuth = new TokenAuth

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()
    val graphqlQuery = GraphqlQuery(query)

    implicit val serialization: Serialization.type =
      org.json4s.native.Serialization

    val request = sttp
      .body(graphqlQuery)
      .contentType("application/json")
      .headers(Map(
        "Authorization" -> s"Bearer ${tokenAuth.getToken().`access_token`}"))
      .post(
        uri"https://m6t2cgd8uc.execute-api.eu-west-2.amazonaws.com/dev/validate")

    request.send()
  }

  implicit val responseSender: GraphqlSender[Response] = (value: Response) => {
    logger.info("sending file format response to graphql server")
    val file: Files = value.files.head
    val fileMatch: Matches = file.matches.head
    val id: String = file.filename.split("/").tail.head
    val format: String = fileMatch.format
    val mime: String = fileMatch.mime
    val basis: String = fileMatch.basis
    val warning: String = fileMatch.warning
    val query: String = s"""
        mutation {
          createFileInfo(id: "$id", input: {format: "$format", mime: "$mime", basis: "$basis", warning: "$warning"}) {
              id
          }
        }"""

    sendQuery(query)
  }

  implicit val virusCheckSender: GraphqlSender[VirusCheckResult] =
    (value: VirusCheckResult) => {
      logger.info("sending virus check status to graphql server")
      val fileId = value.key.split("/").tail.head
      val virusStatus = if (value.clean) "Clean" else "Virus"
      val query: String =
        s"""
           mutation {
             updateFileVirusCheckStatus(fileId: "$fileId", virusStatus: "$virusStatus") {
               id
             }
           }
         """.stripMargin
      sendQuery(query)
    }

  implicit val checksumCheckSender: GraphqlSender[ChecksumCheckResult] =
    (value: ChecksumCheckResult) => {
      logger.info("sending checksum result to graphql server")
      val fileId = value.key.split("/").tail.head
      val checksum = value.checksum
      val query: String =
        s"""
           mutation {
             updateFileChecksumStatus(fileId: "$fileId", checksum: "$checksum") {
               id
             }
           }
         """.stripMargin
      sendQuery(query)
    }
}

object GraphqlSyntax {

  implicit class GraphqlWriterOps[A](value: A) {
    def sendToGraphqlServer(implicit w: GraphqlSender[A]): Unit =
      w.sendToGraphqlServer(value)
  }

}
