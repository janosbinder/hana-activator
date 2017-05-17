import org.apache.http.HttpResponse
import org.apache.http.client.methods.{HttpDelete, HttpPut}

/**
  * Created by jbinder on 16.05.17.
  */
class HanaActivationClient(hanaServer: String, hanaPort: Int, hanaUser: String, hanaPassword: String) extends HanaClient(hanaServer, hanaPort, hanaUser, hanaPassword) {
  def getRoute(route: String): String = {
    // See /sap/hana/xs/dt/base/.xsaccess for the routes
    val routes = Map(
      "FILE" -> "/file",
      "WORKSPACE" -> "/workspace",
      "IMPORT" -> "/xfer/import",
      "EXPORT" -> "/xfer/export",
      "METADATA" -> "/metadata",
      "CHANGE" -> "/change",
      "INFO" -> "/info")

    routes.get(route) match {
      case Some(r) => return s"http://$hanaServer:$hanaPort/sap/hana/xs/dt/base$r"
      case None => throw new HanaClientException(s"$route does not exists")
    }
  }

  def delete(path: String): HttpResponse = {
    val deleteCall = new HttpDelete(getRoute("FILE") + "/" + path)
    val response = super.executeRequest(deleteCall)
    return response
  }

  def activate(path: String): HttpResponse = {
    val activateCall = new HttpPut(getRoute("FILE") + "/" + path + "?parts=meta")
    activateCall.addHeader("X-Requested-With", "XMLHttpRequest")
    activateCall.addHeader("Content-Type", "application/json")
    activateCall.addHeader("SapBackPack", "{\"MassTransfer\":true, \"Activate\":true}")
    val response = super.executeRequest(activateCall)
    return response

  }
}
