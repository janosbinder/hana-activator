import java.io.File
import java.nio.charset.Charset

import org.apache.commons.io.IOUtils
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
          val url_regex = """^(https?):\/\/([^:\/\s]+):(\d+)((\/\w+)*)$""".r
          value match {
            case url_regex(protocol, host, port, dest, _*) =>
              // drop is for the starting '/'
              nextOption(map ++ Map('protocol -> protocol) ++ Map('host -> host) ++ Map('port -> port.toInt) ++ Map('dest -> dest.drop(1)), tail)
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
    if (singlemode.isDefined) {
      val dest = options.getOrElse('dest, "").toString
      val path = options.getOrElse('path, "").toString
      val fullpath = dest + "/" + path
      var response : HttpResponse = null
      singlemode.get match {
        case 'createfile => response = hanaClient.create(dest, path, false)
        case 'createdirectory => response = hanaClient.create(dest, path, true)
        case 'activate => response = hanaClient.activate(fullpath)
        case 'delete => {
          if (path == "") {
            response = hanaClient.delete(dest)
          }
          else {
            response = hanaClient.delete(fullpath)
          }
        }
        case 'uploadfile => {
          val file = new File(path)
          response = hanaClient.putFile(fullpath, file)
        }
        // TODO case 'importpackage => hanaClient.getPackage()
        case 'exportpackage => {
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
        println("Content" + IOUtils.toString(response.getEntity.getContent, Charset.defaultCharset))
        println("Headers" + response.getAllHeaders.map(x => x.getName + ": " + x.getValue).mkString(";"))
      }

    }
    hanaClient.close()
  }
}
