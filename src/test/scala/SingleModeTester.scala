import java.nio.charset.Charset
import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Files, Paths}

import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.util.EntityUtils

/**
  * Created by jbinder on 03.07.17.
  * This tester should be called with "SingleModeTester user password url"
  */
object SingleModeTester {
  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      println("This tester funtion should be called with 3 parameters - SingleModeTester user password url")
      sys.exit(1)
    }
    val user = args(0)
    val password = args(1)
    val url_regex = """^(https?):\/\/([^:\/\s]+):(\d+)((\/\w+)*)$""".r
    args(2) match {
      case url_regex(protocol, host, port, dest, _*) =>
        // drop is for the starting '/'
        startTesting(user, password, protocol, host, port.toInt, dest.drop(1))
      case _ => {
        println("url wrongly provided: " + args(2))
        sys.exit(1)
      }
    }
  }

  def startTesting(user: String, password: String, protocol: String, host : String, port: Int, dest: String) : Unit = {
    val hanaClient = new HanaActivationClient(protocol, host, port, user, password, dest)
    val sandbox = "sandbox"
    // Simple file/directory creation, activation and deletion
    stopOnInvalidResponse(hanaClient.create(dest, sandbox, true), "hanaClient.create(dest, sandbox, true", Set(201))
    stopOnInvalidResponse(hanaClient.create(dest, sandbox + "/temp.xsjs", false), "hanaClient.create(dest, \"/temp.xsjs\", false", Set(201))
    // stopOnInvalidResponse(hanaClient.activate(s"$dest/$sandbox/temp.xsjs"), "hanaClient.activate(s\"$dest/$sandbox/temp.xsjs\")")
    stopOnInvalidResponse(hanaClient.activate(s"$dest/$sandbox"), "hanaClient.activate(s\"$dest/$sandbox\")")
    stopOnInvalidResponse(hanaClient.delete(s"$dest/$sandbox/temp.xsjs"), "hanaClient.delete(s\"$dest/$sandbox/temp.xsjs\")",Set(204))
    stopOnInvalidResponse(hanaClient.delete(s"$dest/$sandbox"), "hanaClient.delete(s\"$dest/$sandbox\")",Set(204))

    // Simple file/directory creation, file upload, activation and deletion
    val dummyFileLocation = createDummyFile
    val dummyFile = new File(dummyFileLocation)
    stopOnInvalidResponse(hanaClient.create(dest, sandbox, true), "hanaClient.create(dest, sandbox, true", Set(201))
    stopOnInvalidResponse(hanaClient.create(dest, sandbox + "/hello.xsjs", false), "hanaClient.create(dest, \"/hello.xsjs\", false", Set(201))
    stopOnInvalidResponse(hanaClient.putFile(s"$dest/$sandbox/hello.xsjs", dummyFile), "hanaClient.putFile(s\"$dest/$sandbox/hello.xsjs\", dummyFile)")
    // stopOnInvalidResponse(hanaClient.activate(s"$dest/$sandbox/hello.xsjs"), "hanaClient.activate(s\"$dest/$sandbox/hello.xsjs\")")
    stopOnInvalidResponse(hanaClient.activate(s"$dest/$sandbox"), "hanaClient.activate(s\"$dest/$sandbox\")")
    stopOnInvalidResponse(hanaClient.delete(s"$dest/$sandbox/hello.xsjs"), "hanaClient.delete(s\"$dest/$sandbox/hello.xsjs\")",Set(204))
    stopOnInvalidResponse(hanaClient.delete(s"$dest/$sandbox"), "hanaClient.delete(s\"$dest/$sandbox\")",Set(204))
    Files.deleteIfExists(dummyFile.toPath)
    hanaClient.close()
  }

  def createDummyFile() : String = {

    val contents =
      """
        |const message = "Hello beautiful world!";
        |
        |$.response.status = 200;
        |$.response.setBody(message);
      """.stripMargin
    val routePath = Paths.get(".").toAbsolutePath.normalize.toString
    val helloPath = routePath + File.separator + "hello.xsjs"
    val bw = new BufferedWriter(new FileWriter(new File(helloPath), true))
    bw.write(contents)
    bw.close()
    return helloPath
  }

  def stopOnInvalidResponse(response : CloseableHttpResponse, executedMethod : String, expectedStatus : Set[Int] = Set(200,201,204)) = {
    if (response == null) {
      println("ERROR: No response from HANA service")
    } else {
      val status = response.getStatusLine
      if (!expectedStatus(status.getStatusCode)) {
        println("ERROR: Invalid status code")
        println(s"Called method: $executedMethod")
        println("HTTP Status code: " + status.getStatusCode)
        println("Message" + status.getReasonPhrase)
        val content = response.getEntity.getContent
        println("Content" + IOUtils.toString(response.getEntity.getContent, Charset.defaultCharset))
        println("Headers" + response.getAllHeaders.map(x => x.getName + ": " + x.getValue).mkString(";"))
        sys.exit(1)
      } else {
        println(s"Succesfully called method: $executedMethod")
      }
      EntityUtils.consumeQuietly(response.getEntity)
      response.close()
    }
  }

}
