/**
  * Created by jbinder on 15.05.17.
  */
object ActivatorClient {
  def main(args: Array[String]): Unit = {
    val arglist = args.toList
    type OptionMap = Map[Symbol, Any]

    def nextOption(map: OptionMap, list: List[String]) : OptionMap = {
      def isSwitch(s:String) = (s(0) == '-')
      list match {
        case Nil => map
      }
    }

    val hanaClient = new HanaClient("srv-6712", 8012, "1", "2")
    hanaClient.close()
  }
}
