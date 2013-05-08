package snap
package cache

import activator.properties.ActivatorProperties
/**
 * This class is able to read a cache properties file and
 *  give us the information stored within it, as well as save new info...
 *
 *  NOTE - This is not threadsafe or anything.  It should probably be hidden behind an actor.
 *
 *  TODO - Auto-Save + Auto-reload on file changes?
 */
class CacheProperties(location: java.io.File) {
  val props = {
    val tmp = new java.util.Properties
    if (!location.exists) IO.touch(location)
    Using.fileInputStream(location)(tmp.load)
    tmp
  }

  def cacheIndexHash = props.getProperty(Constants.CACHE_HASH_PROPERTY)
  def cacheIndexHash_=(newId: String) = {
    props.setProperty(Constants.CACHE_HASH_PROPERTY, newId)
  }
  // TODO - Binary compatibility version?

  def reload(): Unit = {
    props.clear()
    Using.fileInputStream(location)(props.load)
  }
  def save(msg: String = "Automatically updated properties."): Unit =
    Using.fileOutputStream(false)(location)(props.store(_, msg))
}

object CacheProperties {
  def cacheDir = new java.io.File(ActivatorProperties.ACTIVATOR_TEMPLATE_CACHE())
  def default = new CacheProperties(new java.io.File(cacheDir, Constants.CACHE_PROPS_FILENAME))
}
