import java.io.File

import org.apache.http.HttpResponse

/**
  * Created by jbinder on 15.05.17.
  */
object ActivatorClient {
  def main(args: Array[String]): Unit = {
    val arglist = args.toList
    type OptionMap = Map[Symbol, Any]

    def nextOption(map: OptionMap, list: List[String]): OptionMap = {
      list match {
        case Nil => map
        case "--url" :: value :: tail => {
          val url_regex = """^(https?):\/\/?([^:\/\s]+):(\d+)((\/\w+)*)$""".r
          value match {
            case url_regex(protocol, host, port, dest, _*) =>
              nextOption(map ++ Map('protocol -> protocol) ++ Map('host -> host) ++ Map('port -> port.toInt) ++ Map('dest -> dest), tail)
            case _ => {
              println("url wrongly provided: " + value)
              sys.exit(1)
            }
          }
        }
        case "--user" :: value :: tail =>
          nextOption(map ++ Map('user -> value), tail)
        case "--password" :: value :: tail =>
          nextOption(map ++ Map('password -> value), tail)
        // Single operations
        case "--create-file" :: value :: tail =>
          nextOption(map ++ Map('singleoperation -> 'createfile) ++ Map('path -> value), tail)
        case "--create-directory" :: value :: tail =>
          nextOption(map ++ Map('singleoperation -> 'createdirectory) ++ Map('path -> value), tail)
        case "--activate" :: value :: tail =>
          nextOption(map ++ Map('singleoperation -> 'activate) ++ Map('path -> value), tail)
        case "--upload-file" :: value :: tail =>
          nextOption(map ++ Map('singleoperation -> 'uploadfile) ++ Map('path -> value), tail)
        case "--delete" :: value :: tail =>
          nextOption(map ++ Map('singleoperation -> 'delete) ++ Map('path -> value), tail)
        case "--delete" :: tail =>
          nextOption(map ++ Map('singleoperation -> 'delete) ++ Map('path -> ""), tail)
        case "--import-package" :: value :: tail =>
          nextOption(map ++ Map('singleoperation -> 'importpackage) ++ Map('path -> value), tail)
        case "--export-package" :: value :: tail =>
          nextOption(map ++ Map('singleoperation -> 'exportpackage) ++ Map('path -> value), tail)
        // case string :: Nil =>  nextOption(map ++ Map('infile -> string), list.tail)
        case option :: tail => {
          println("Unknown option " + option)
          sys.exit(1)
        }
      }
    }

    val options = nextOption(Map(), arglist)
    val hanaClient = new HanaActivationClient(options('protocol).toString, options('host).toString, options('port).asInstanceOf[Int], options('user).toString, options('password).toString, options('dest).toString)
    val singlemode = options.get('singleoperation)
    if (singlemode != None) {
      val dest = options.get('dest).toString
      val path = options.get('path).toString
      val fullpath = path + "/" + dest
      var response : HttpResponse = null
      singlemode match {
        case Some('createfile) => response = hanaClient.create(dest, path, false)
        case Some('createdirectory) => response = hanaClient.create(dest, path, true)
        case Some('activate) => response = hanaClient.activate(fullpath)
        case Some('delete) => {
          if (path == "") {
            response = hanaClient.delete(dest)
          }
          else {
            response = hanaClient.delete(fullpath)
          }
        }
        case Some('uploadfile) => {
          val file = new File(path)
          response = hanaClient.putFile(fullpath, file)
        }
        // TODO case Some('importpackage) => hanaClient.getPackage()
        case Some('exportpackage) => {
          val file = new File(fullpath)
          val response = hanaClient.importFile(dest, file)
        }
      }
      if (response == null) {
        println("ERROR: No response from HANA service")
      } else {
        println("Response from HANA service:")
        val status = response.getStatusLine
        println("HTTP Status code: " + status.getStatusCode)
        println("Message" + status.getReasonPhrase)
        val content =  response.getEntity.getContent

        println("Content" + response.getEntity.getContent)
      }

    }
    hanaClient.close()
  }
}
