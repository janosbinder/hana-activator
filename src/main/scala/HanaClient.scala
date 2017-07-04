import java.io.Closeable
import java.util.Base64
import java.nio.charset.StandardCharsets

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{CloseableHttpResponse, HttpHead, HttpPost, HttpUriRequest}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.{BasicCookieStore, CloseableHttpClient, HttpClients}

/**
  * Created by jbinder on 16.05.17.
  */
class HanaClient(val protocol: String, val host: String, val port: Int, val user: String, val password: String) extends Closeable {
  private val client: CloseableHttpClient = createClient
  val successCodes = Set(200, 201, 204)
  val response: HttpResponse = fetchXCSRFToken(client)

  val cookies: String = response.getHeaders("set-cookie").map(_.getValue).mkString(";")
  val token: String = response.getHeaders("x-csrf-token").map(_.getValue).mkString(";")
  if (cookies == "" || token == "") {
    throw new HanaClientException(s"X-CSRF-Token or cookie receipt failed - Check authentication at $host:$port with user $user")
  }

  override def close(): Unit = {
    if (token != "") {
      val logoutEndpoint = s"$protocol://$host:$port/sap/hana/xs/formLogin/logout.xscfunc"
      val logoutCall = new HttpPost(logoutEndpoint)
      logoutCall.addHeader("X-CSRF-Token", token)
      client.execute(logoutCall)
    }
    client.close()
  }

  def executeRequest(request: HttpUriRequest): CloseableHttpResponse = {
    if (token == "") {
      throw new HanaClientException(s"X-CSRF-Token is missing - Check authentication at $host:$port with user $user")
    }
    val headers = Map("X-CSRF-Token" -> token,
      "Cookie" -> cookies,
      "Orion-Version" -> "1.0")
    headers.foreach { case (k, v) => request.addHeader(k, v) }
    return client.execute(request)
  }

  private def fetchXCSRFToken(client: HttpClient): HttpResponse = {
    val authenticationHeader = Base64.getEncoder.encodeToString(s"$user:$password".getBytes(StandardCharsets.UTF_8))
    val headers = Map("X-CSRF-Token" -> "Fetch",
      "X-Requested-With" -> "XMLHttpRequest",
      "Authorization" -> s"Basic $authenticationHeader")
    val authenticationEndpoint =
      s"$protocol://$host:$port/sap/hana/xs/formLogin/token.xsjs"
    val xscrfCall = new HttpHead(authenticationEndpoint)
    headers.foreach { case (k, v) => xscrfCall.addHeader(k, v) }
    client.execute(xscrfCall)
  }

  private def createClient: CloseableHttpClient = {
    val globalConfig = RequestConfig.custom.setCookieSpec("default").build
    val cookieStore = new BasicCookieStore
    val context = HttpClientContext.create
    context.setCookieStore(cookieStore)
    HttpClients.custom.setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore).build
  }
}
