package snap
package cache
package generator

import java.io.File

/**
 * This trait represents something that can read template metadata from an expanded template directory.
 */
trait MetadataReader {
  /** Reads the metadata file and returns the template metadata. */
  def read(templateDir: File): Option[TemplateMetadata]
}
object MetadataReader {
  implicit val default: MetadataReader = PropertiesFileMetadataReader
}

object PropertiesFileMetadataReader extends MetadataReader {
  // Thing we probably want to do ->
  // 1. pull id from the directory name?
  // 2. Pull timestamp from something else?

  def read(metadataFile: File): Option[TemplateMetadata] = util.Try({
    // TODO - useful errors
    val props = new java.util.Properties
    val reader = new java.io.FileReader(metadataFile)
    try props.load(reader)
    finally reader.close()
    propsToMeta(props)
  }).toOption

  def propsToMeta(props: java.util.Properties): TemplateMetadata = {
    TemplateMetadata(
      id = props.getProperty("id"),
      name = props.getProperty("name"),
      title = props.getProperty("title"),
      timeStamp = props.getProperty("timestamp").toLong,
      description = props.getProperty("description"),
      tags = props.getProperty("tags").split(","))
  }
}
