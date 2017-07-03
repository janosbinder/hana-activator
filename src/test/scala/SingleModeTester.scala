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
    hanaClient.close()
  }

}
