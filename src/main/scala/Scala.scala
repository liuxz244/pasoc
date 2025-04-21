package scala

import scala.io.Source
import java.io.PrintWriter

object FilePrepender {
  def prependLine(filename: String, line: String): Unit = {
    // 读取文件内容
    val fileContent = Source.fromFile(filename).getLines().toList

    // 添加新行到文件内容的开头
    val newContent = line :: fileContent

    // 写回文件
    val writer = new PrintWriter(filename)
    newContent.foreach(writer.println)
    writer.close()
  }
}