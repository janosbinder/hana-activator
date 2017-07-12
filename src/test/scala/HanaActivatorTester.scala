import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Files, Paths}

/**
  * Created by jbinder on 12.07.17.
  */
object HanaActivatorTester {
  private val extensions = List("xsapp",
    "xsprivileges",
    "hdbstructure",
    "hdbprocedure",
    "xsjslib",
    "html",
    "hdbrole",
    "xsaccess")

  def main(args: Array[String]): Unit = {
    val workPath = Paths.get(".").toAbsolutePath.normalize.toString
    val filename = "dummy"
    extensions.foreach {
      e =>
        val helloPath = workPath + File.separator + filename + "." + e
        val bw = new BufferedWriter(new FileWriter(new File(helloPath), true))
        bw.write(" ")
        bw.close()
    }
    val workDir = new File(workPath)
    val orderedExtension = workDir.listFiles.toList.sorted(new HanaExtensionComparator)
    extensions.foreach { e =>
      val helloPath = workPath + File.separator + filename + "." + e
      Files.deleteIfExists(Paths.get(helloPath))
    }
  }
}
