package actors

import play.mvc.WebSocket
import org.codehaus.jackson.JsonNode

class StubOut() extends WebSocket.Out[JsonNode]() {
  var expected:JsonNode = null

  def write(node: JsonNode) {
    expected = node
  }

  def close() {}
}
