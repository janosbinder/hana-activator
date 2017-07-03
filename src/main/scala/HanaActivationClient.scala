import java.io.{File, FileOutputStream, IOException}
import java.nio.file.Files

import org.apache.commons.io.IOUtils
import org.apache.http.{Consts, HttpEntity, HttpResponse}
import org.apache.http.client.methods._
import org.apache.http.entity.{ContentType, FileEntity, StringEntity}
import org.apache.http.util.EntityUtils

/**
  * Created by jbinder on 16.05.17.
  */
class HanaActivationClient(protocol: String, host: String, port: Int, user: String, password: String, dest: String) extends HanaClient(protocol, host, port, user, password) {
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
      case Some(r) => return s"$protocol://$host:$port/sap/hana/xs/dt/base$r"
      case None => throw new HanaClientException(s"$route does not exists")
    }
  }

  // saveFile
  def putFile(path: String, file: File): HttpResponse = {
    val transferFileCall = new HttpPut(getRoute("FILE") + "/" + path)
    addContentType(file, transferFileCall)
    transferFileCall.setEntity(createEntity(file))
    val response = super.executeRequest(transferFileCall)
    return response
  }

  // getFile
  def getFile(path: String): HttpResponse = {
    val getFileCall = new HttpGet(getRoute("FILE") + "/" + path)
    val response = super.executeRequest(getFileCall)
    return response
  }

  def getPackage(path: String, exportFileName: String): HttpResponse = {
    val exportPackageCall = new HttpGet(getRoute("EXPORT") + "/" + path + ".zip")
    val response = super.executeRequest(exportPackageCall)
    val statusCode = response.getStatusLine.getStatusCode
    if (statusCode != 201) {
      throw new HanaClientException(s"Service responded with status $statusCode instead of 201")
    }
    val output = new FileOutputStream(exportFileName)
    IOUtils.copy(response.getEntity.getContent, output)
    output.flush()
    output.close()
    return response

  }


  // importFile
  def importFile(path: String, file: File): HttpResponse = {
    val importFileCall = new HttpPost(getRoute("IMPORT") + "/" + path + "/" + file.getName + "?force=true")
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
        val importLocationCall = new HttpPut(s"$protocol://$host:$port$importLocation")
        importLocationCall.setEntity(createEntity(file))
        addContentType(file, importLocationCall)
        val fileLength = file.length - 1L
        importLocationCall.addHeader("Content-Range", s"bytes 0-$fileLength/$fileLength")
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
    val headers = Map("X-Requested-With" -> "XMLHttpRequest",
      "SapBackPack" -> "{\"MassTransfer\":true, \"Activate\":true}",
      "Content-Type" -> "application/json")
    headers.foreach { case (k, v) => activateCall.addHeader(k, v) }
    val response = super.executeRequest(activateCall)
    return response

  }

  // createRepoEntry
  def create(path: String, fileName: String, isDir: Boolean): HttpResponse = {
    val createCall = new HttpPost(getRoute("FILE") + "/" + path)
    val stringEntity = new StringEntity("{\"Name\": \"" + fileName + "\", \"Directory\": \"" + isDir.toString + "\"}", Consts.UTF_8)
    createCall.setEntity(stringEntity)
    val response = super.executeRequest(createCall)
    val statusCode = response.getStatusLine.getStatusCode
    if (statusCode != 201) {
      throw new HanaClientException(s"Service responded with status $statusCode instead of 201 - artifact probably exist - check $path")
    }
    return response
  }

  private def addContentType(file: File, request: HttpUriRequest): Unit = {
    var contentType: String = ""
    try {
      if (file.getName.endsWith(".zip")) {
        contentType = "application/x-zip-compressed"
      } else {
        contentType = Files.probeContentType(file.toPath)
      }
    } catch {
      case ex: IOException => {
        contentType = ContentType.APPLICATION_OCTET_STREAM.getMimeType
      }
    } finally {
      request.addHeader("Content-Type", contentType + "; charset=utf-8")
    }
  }

  @throws[IOException]
  private def createEntity(file: File): HttpEntity = {
    val fileEntity = new FileEntity(file)
    fileEntity.setContentType(Files.probeContentType(file.toPath))
    fileEntity.setChunked(false)
    return fileEntity
  }

}
