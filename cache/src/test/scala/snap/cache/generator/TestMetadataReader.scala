package snap
package cache
package generator

import org.junit.Assert._
import org.junit._
import java.io.File

class TestMetadataReader {
  val testMeta = TemplateMetadata(
    id = "f9a3508cefd6408c6b993b5d90b328a72c1779d8",
    name = "reactive-stocks",
    title = "Reactive Stocks",
    timeStamp = 1,
    description = """The Reactive Stocks application uses Java, Scala, Play Framework, and Akka to illustrate a reactive app.  The tutorial in this example will teach you the reactive basics including Reactive Composition and Reactive Push.""",
    tags = Seq("Sample", "java", "scala", "play framework", "akka", "reactive"))

  val testMeta2 = TemplateMetadata(
    id = "a5227c77d39109b6550a47758c2f9a1341e06524",
    name = "hello-scala",
    title = "Hello Scala!",
    timeStamp = 1,
    description = """Scala is a general purpose programming language designed to express common programming patterns in a concise, elegant, and type-safe way.  This very simple Scala application will get you started building and testing standalone Scala apps.  This app uses Scala 2.10 and ScalaTest.""",
    tags = Seq("Basics", "scala", "starter"))

  private def saveMetaData(meta: TemplateMetadata, file: File): Unit = {
    val props = new java.util.Properties
    props.put("id", meta.id)
    props.put("name", meta.name)
    props.put("title", meta.title)
    props.put("description", meta.description)
    props.put("timestamp", meta.timeStamp.toString)
    props.put("tags", meta.tags.mkString(","))
    val writer = new java.io.FileWriter(file)
    try props.store(writer, "Test save metadata.")
    finally writer.close()
  }

  private def samplePropsFile: String =
    """
id=a5227c77d39109b6550a47758c2f9a1341e06524
name=hello-scala
title=Hello Scala!
timestamp=1
description=Scala is a general purpose programming language designed to express common programming patterns in a concise, elegant, and type-safe way.  This very simple Scala application will get you started building and testing standalone Scala apps.  This app uses Scala 2.10 and ScalaTest.
tags=Basics,scala,starter
""".stripMargin

  @Test
  def testWriteAndRead(): Unit = {
    val file = File.createTempFile("activator", "properties")
    saveMetaData(testMeta, file)
    val reader = implicitly[MetadataReader]
    val result = reader.read(file)
    assertEquals("Failed to read metadata from properties file", Some(testMeta), result)
    ()
  }

  @Test
  def testWriteSampleAndRead(): Unit = {
    val file = File.createTempFile("activator", "properties")
    IO.write(file, samplePropsFile)
    val reader = implicitly[MetadataReader]
    val result = reader.read(file)
    assertEquals("Failed to read metadata from properties file", Some(testMeta2), result)
    ()
  }
}
