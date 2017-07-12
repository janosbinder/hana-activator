import java.io.File
import org.apache.commons.io.FilenameUtils

/**
  * Created by jbinder on 12.07.17.
  */
sealed class HanaExtensionComparator extends Ordering[File] {
  private val activationPriorities = Map("xsapp" -> 1,
    "xsprivileges" -> 2,
    // isDirectory()) -> 3,
    "hdbstructure" -> 4,
    "hdbprocedure" -> 5,
    "xsjslib" -> 6,
    // default -> 7
    "hdbrole" -> 8,
    "xsaccess" -> 9)

  override def compare(x: File, y: File): Int = {
    def getActivationPriority(file: File): Int = {
      if (file.isDirectory) return 3
      val extension = FilenameUtils.getExtension(file.getName.toLowerCase)
      return activationPriorities.getOrElse(extension, 7)
    }
    if (getActivationPriority(x) == getActivationPriority(y)) {
      return x.compareTo(y)
    }
    return getActivationPriority(x) - getActivationPriority(y)
  }
}
