import java.io.Closeable
import java.util.Base64
import java.nio.charset.StandardCharsets

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpHead, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.{BasicCookieStore, CloseableHttpClient, HttpClients}

/**
  * Created by jbinder on 16.05.17.
  */
class HanaClient(val hanaServer:String, val hanaPort:Int, val hanaUser:String, val hanaPassword:String) extends Closeable {
  val client : CloseableHttpClient = createClient
  val response : HttpResponse= fetchXCSRFToken(client)

  val cookies : String = response.getHeaders("set-cookie").map(h => h.getValue).mkString(";")
  val token : String = response.getHeaders("x-csrf-token").map(h => h.getValue).mkString(";")
  if( cookies == "" || token == "") {
      throw new HanaClientException(s"X-CSRF-Token token receipt failed - Check authentication at $hanaServer:$hanaPort with user $hanaUser")
  }

  override def close(): Unit = {
    if (token != "") {
      val logoutEndpoint = s"$hanaServer:$hanaPort/sap/hana/xs/formLogin/logout.xscfunc"
      val logoutCall = new HttpPost(logoutEndpoint)
      logoutCall.addHeader("X-CSRF-Token", token)
      client.execute(logoutCall)
    }
    client.close()
  }

  private def fetchXCSRFToken(client : HttpClient) : HttpResponse = {
    val authenticationHeader = Base64.getEncoder.encodeToString(s"$hanaUser:$hanaPassword".getBytes(StandardCharsets.UTF_8))
    val headers = Map("X-CSRF-Token" -> "Fetch",
                      "X-Requested-With" -> "XMLHttpRequest",
                      "Authorization" -> s"Basic $authenticationHeader")
    val authenticationEndpoint =
      s"http://$hanaServer:$hanaPort/sap/hana/xs/formLogin/token.xsjs"
    val xscrfCall = new HttpHead(authenticationEndpoint)
    headers.foreach { case (k, v) => xscrfCall.addHeader(k, v) }
    client.execute(xscrfCall)
  }

  private def createClient : CloseableHttpClient = {
    val globalConfig = RequestConfig.custom.setCookieSpec("default").build
    val cookieStore = new BasicCookieStore
    val context = HttpClientContext.create
    context.setCookieStore(cookieStore)
    HttpClients.custom.setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore).build
  }
}
