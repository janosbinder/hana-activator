import java.io.File

/**
  * Created by jbinder on 04.07.17.
  */
class HanaBatchClient(protocol: String, host: String, port: Int, user: String, password: String, dest: String) extends HanaActivationClient(protocol, host, port, user, password, dest) {
  def copyDirectory(path: String, directory: File) : Unit = {
    if(directory.isDirectory) {
      // TODO what to do if the dir exists?
      val res = this.create(path, directory.getName, true)
      // TODO handle res and store it
      val files = directory.listFiles.toList.sorted(new HanaExtensionComparator)
      // TODO implement handle return values
      files.foreach(f => this.copyDirectory(path + "/" + directory.getName, f))
      // call copyDirectory on it
    } else {
      // TODO what to do if the file exists?
      // TODO do something with the result
      val res = this.create(path, directory.getName, false)
      val res2 = this.putFile(path, directory)
      // it is a file copy it over
    }
    // do some activation on the directory if the activation fails
  }
}
