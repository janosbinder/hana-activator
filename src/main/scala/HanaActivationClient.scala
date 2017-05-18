import java.io.{File, FileOutputStream, IOException}
import java.nio.file.Files

import org.apache.commons.io.IOUtils
import org.apache.http.{HttpEntity, HttpResponse, Consts}
import org.apache.http.client.methods.{HttpDelete, HttpPost, HttpPut, HttpUriRequest}
import org.apache.http.entity.{ContentType, FileEntity, StringEntity}
import org.apache.http.util.EntityUtils

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

  // saveFile
  def putFile(path: String, file: File) : HttpResponse = {
    val transferFileCall = new HttpPut(getRoute("FILE") + "/" + path)
    addContentType(file, transferFileCall)
    transferFileCall.setEntity(createEntity(file))
    val response = super.executeRequest(transferFileCall)
    return response
  }

  // getFile
  def getFile(path: String) : HttpResponse = {
    val getFileCall = new HttpPut(getRoute("FILE") + "/" + path)
    val response = super.executeRequest(getFileCall)
    return response
  }

  def getPackage(path: String, exportFileName: String): Unit = {
    val exportPackageCall = new HttpPut(getRoute("EXPORT") + "/" + path + ".zip")
    val response = super.executeRequest(exportPackageCall)
    val statusCode = response.getStatusLine.getStatusCode
    if (statusCode != 201) {
      throw new HanaClientException(s"Service responded with status $statusCode instead of 201")
    }
    val output = new FileOutputStream(exportFileName)
    IOUtils.copy(response.getEntity.getContent, output)
    output.flush()
    output.close()

  }


  // importFile
  def importFile(path: String, file: File) : HttpResponse = {
    val importFileCall = new HttpPost(getRoute("IMPORT") + "/" + path + "?force=true")
    importFileCall.addHeader("Slug", file.getName)
    importFileCall.addHeader("X-Create-Options", "no-overwrite")
    importFileCall.addHeader("X-Xfer-Content-Length", file.length.toString)
    importFileCall.addHeader("X-Xfer-Options", "raw")
    addContentType(file, importFileCall)
    val response = super.executeRequest(importFileCall)
    val statusCode = response.getStatusLine.getStatusCode
    if (statusCode != 200) {
      throw new HanaClientException(s"Service responded with status $statusCode instead of 200")
    }
    else {
      val importLocation = response.getHeaders("Location").map(_.getValue).mkString(";")
      if (importLocation == "") {
        throw new HanaClientException("No location header in service response for import")
      } else {
        EntityUtils.consumeQuietly(response.getEntity)
        val importLocationCall = new HttpPut(s"http://$hanaServer:$hanaPort$importLocation")
        importLocationCall.setEntity(createEntity(file))
        addContentType(file, importLocationCall)
        val fileLenght = file.length - 1L
        importLocationCall.addHeader("Content-Range", s"bytes 0-$fileLenght/$fileLenght")
        val responseImportLocationCall = super.executeRequest(importLocationCall)
        EntityUtils.consumeQuietly(responseImportLocationCall.getEntity)
        return responseImportLocationCall
      }
    }
  }

  // delete
  def delete(path: String): HttpResponse = {
    val deleteCall = new HttpDelete(getRoute("FILE") + "/" + path)
    val response = super.executeRequest(deleteCall)
    return response
  }

  // activate
  def activate(path: String): HttpResponse = {
    val activateCall = new HttpPut(getRoute("FILE") + "/" + path + "?parts=meta")
    activateCall.addHeader("X-Requested-With", "XMLHttpRequest")
    activateCall.addHeader("Content-Type", "application/json")
    activateCall.addHeader("SapBackPack", "{\"MassTransfer\":true, \"Activate\":true}")
    val response = super.executeRequest(activateCall)
    return response

  }

  // exportPackage
  def create(path: String, fileName: String, isDir: Boolean): Unit = {
    val createCall = new HttpPost(getRoute("FILE") + "/" + path)
    val stringEntity = new StringEntity("{\"Name\": \"" + fileName + "\", \"Directory\": \"" + isDir.toString + "\"}", Consts.UTF_8)
    createCall.setEntity(stringEntity)
    val response = super.executeRequest(createCall)
  }

  private def addContentType(file: File, request: HttpUriRequest) : Unit = {
    var contentType : String = ""
    try {
      if (file.getName.endsWith(".zip")) {
        contentType = "application/x-zip-compressed"
      } else {
        contentType = Files.probeContentType(file.toPath)
      }
    } catch {
      case ex : IOException => {
        contentType = ContentType.APPLICATION_OCTET_STREAM.getMimeType
      }
    } finally {
      request.addHeader("Content-Type", contentType + "; charset=utf-8")
    }
  }

  @throws[IOException]
  private def createEntity(file: File) : HttpEntity = {
    val fileEntity = new FileEntity(file)
    fileEntity.setContentType(Files.probeContentType(file.toPath))
    fileEntity.setChunked(false)
    return fileEntity
  }

}
