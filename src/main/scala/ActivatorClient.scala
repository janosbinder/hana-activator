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
        // case string :: Nil =>  nextOption(map ++ Map('infile -> string), list.tail)
        case option :: tail => {
          println("Unknown option " + option)
          sys.exit(1)
        }
      }
    }

    val options = nextOption(Map(), arglist)
    val hanaClient = new HanaActivationClient(options('protocol).toString, options('host).toString, options('port).asInstanceOf[Int], options('user).toString, options('password).toString, options('dest).toString)
    hanaClient.delete("reporter_jbi/deleteme.txt")
    hanaClient.close()
  }
}
