package snap
package cache
package generator

import org.junit.Assert._
import org.junit._
import java.io.File

class TestMetadataReader {
  val testMeta = UserDefinedTemplateMetadata(
    name = "reactive-stocks",
    title = "Reactive Stocks",
    description = """The Reactive Stocks application uses Java, Scala, Play Framework, and Akka to illustrate a reactive app.  The tutorial in this example will teach you the reactive basics including Reactive Composition and Reactive Push.""",
    tags = Seq("Sample", "java", "scala", "play framework", "akka", "reactive"))

  val testMeta2 = UserDefinedTemplateMetadata(
    name = "hello-scala",
    title = "Hello Scala!",
    description = """Scala is a general purpose programming language designed to express common programming patterns in a concise, elegant, and type-safe way.  This very simple Scala application will get you started building and testing standalone Scala apps.  This app uses Scala 2.10 and ScalaTest.""",
    tags = Seq("Basics", "scala", "starter"))

  private def saveMetaData(meta: UserDefinedTemplateMetadata, file: File): Unit = {
    val props = new java.util.Properties
    props.put("name", meta.name)
    props.put("title", meta.title)
    props.put("description", meta.description)
    props.put("tags", meta.tags.mkString(","))
    val writer = new java.io.FileWriter(file)
    try props.store(writer, "Test save metadata.")
    finally writer.close()
  }

  private def samplePropsFile: String =
    """
name=hello-scala
title=Hello Scala!
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
